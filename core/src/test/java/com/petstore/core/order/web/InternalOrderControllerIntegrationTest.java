package com.petstore.core.order.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;

/**
 * Characterizes the fulfillment shipment callback: token-gated,
 * qtyShipped-guarded (replays are no-ops), and driving APPROVED ->
 * PARTIALLY_SHIPPED -> COMPLETED through the state machine. Ordered: one order
 * is walked through its lifecycle.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "petstore.seed=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class InternalOrderControllerIntegrationTest {

	private static final String TOKEN_HEADER = "X-Internal-Token";

	@Value("${petstore.internal.token}")
	private String internalToken;

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@Order(1)
	void partialShipmentMovesAnApprovedOrderToPartiallyShipped() throws Exception {
		String j2ee = loginAndGetToken("j2ee", "j2ee");
		placeSmallApprovedOrder(j2ee, 2);

		mockMvc.perform(post("/api/internal/orders/1001/shipments").header(TOKEN_HEADER, internalToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"shipments\":[{\"itemId\":\"EST-1\",\"qtyShipped\":1}]}")).andExpect(status().isOk());

		mockMvc.perform(get("/api/orders/1001").header("Authorization", "Bearer " + j2ee)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PARTIALLY_SHIPPED"))
				.andExpect(jsonPath("$.lines[0].qtyShipped").value(1));
	}

	@Test
	@Order(2)
	void shippingTheRestCompletesTheOrderWithTheFullAuditTrail() throws Exception {
		mockMvc.perform(post("/api/internal/orders/1001/shipments").header(TOKEN_HEADER, internalToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"shipments\":[{\"itemId\":\"EST-1\",\"qtyShipped\":1}]}")).andExpect(status().isOk());

		String j2ee = loginAndGetToken("j2ee", "j2ee");
		mockMvc.perform(get("/api/orders/1001").header("Authorization", "Bearer " + j2ee)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.lines[0].qtyShipped").value(2))
				.andExpect(jsonPath("$.statusHistory[*].status").value(
						org.hamcrest.Matchers.contains("PENDING", "APPROVED", "PARTIALLY_SHIPPED", "COMPLETED")));
	}

	@Test
	@Order(3)
	void replayingACallbackOnACompletedOrderIsANoOp() throws Exception {
		mockMvc.perform(post("/api/internal/orders/1001/shipments").header(TOKEN_HEADER, internalToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"shipments\":[{\"itemId\":\"EST-1\",\"qtyShipped\":1}]}")).andExpect(status().isOk());

		String j2ee = loginAndGetToken("j2ee", "j2ee");
		mockMvc.perform(get("/api/orders/1001").header("Authorization", "Bearer " + j2ee)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.lines[0].qtyShipped").value(2))
				.andExpect(jsonPath("$.statusHistory.length()").value(4));
	}

	@Test
	void missingOrWrongInternalTokenIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/internal/orders/1001/shipments").contentType(MediaType.APPLICATION_JSON)
				.content("{\"shipments\":[{\"itemId\":\"EST-1\",\"qtyShipped\":1}]}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/internal/orders/1001/shipments").header(TOKEN_HEADER, "wrong-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"shipments\":[{\"itemId\":\"EST-1\",\"qtyShipped\":1}]}"))
				.andExpect(status().isUnauthorized());
	}

	private void placeSmallApprovedOrder(String token, int qty) throws Exception {
		String contact = """
				{"familyName":"ABC","givenName":"XYZ",
				 "address":{"street":["1234 Anywhere Street"],"city":"Palo Alto","state":"CA","zipCode":"94303","country":"USA"},
				 "email":"aaa@bbb.ccc","phone":"555-555-5555"}""";
		String body = """
				{"locale":"en_US","lines":[{"itemId":"EST-1","qty":%d}],"shipTo":%s,"billTo":%s,
				 "creditCard":{"cardNumber":"123456789","cardType":"Meow Card","expiryDate":"04/04"}}""".formatted(qty,
				contact, contact);
		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("APPROVED"));
	}

	private String loginAndGetToken(String username, String password) throws Exception {
		String body = mockMvc
				.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("token").asText();
	}
}
