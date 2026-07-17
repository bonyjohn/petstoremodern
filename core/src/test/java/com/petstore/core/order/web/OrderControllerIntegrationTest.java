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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;

/**
 * Boots the real app against a throwaway MongoDB (seeded catalog + customers) and
 * characterizes the checkout pipeline against the legacy order flow: sequential ids
 * from 1001, server-side pricing, and the PurchaseOrderMDB auto-approval rule.
 * Ordered methods: the id sequence assertions depend on how many orders exist.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "petstore.seed=true")
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
class OrderControllerIntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@Order(1)
	void smallEnUsOrderGetsId1001IsServerPricedAndAutoApproved() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		mockMvc.perform(post("/api/orders")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderBody("en_US", "EST-1", 1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderId").value("1001"))
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.statusHistory[0].status").value("PENDING"))
				.andExpect(jsonPath("$.statusHistory[1].status").value("APPROVED"))
				.andExpect(jsonPath("$.totalValue").value(16.50))
				.andExpect(jsonPath("$.lines[0].itemId").value("EST-1"))
				.andExpect(jsonPath("$.lines[0].productId").value("FI-SW-01"))
				.andExpect(jsonPath("$.lines[0].categoryId").value("FISH"))
				.andExpect(jsonPath("$.lines[0].unitPrice").value(16.50))
				.andExpect(jsonPath("$.lines[0].qtyShipped").value(0));
	}

	@Test
	@Order(2)
	void bigEnUsOrderOver500GetsTheNextIdAndStaysPending() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		// 28 bulldogs at $18.50 = $518.00 — over the $500 auto-approval limit.
		mockMvc.perform(post("/api/orders")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderBody("en_US", "EST-6", 28)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderId").value("1002"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.statusHistory.length()").value(1))
				.andExpect(jsonPath("$.totalValue").value(518.00));
	}

	@Test
	@Order(3)
	void zhCnOrderNeverAutoApprovesEvenWhenTiny() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		mockMvc.perform(post("/api/orders")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderBody("zh_CN", "EST-1", 1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.totalValue").value(142)); // zh_CN price, not en_US
	}

	@Test
	@Order(4)
	void listOwnOrdersReturnsAllOfJ2eesOrders() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	@Order(5)
	void anotherUsersOrderIdReadsAsNotFound() throws Exception {
		String token = loginAndGetToken("shopper", "j2ee");

		mockMvc.perform(get("/api/orders/1001").header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	@Order(6)
	void adminCanReadAnyOrder() throws Exception {
		String token = loginAndGetToken("admin", "admin123");

		mockMvc.perform(get("/api/orders/1001").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId").value("1001"));
	}

	@Test
	void itemWithoutTheRequestedLocaleIsRejectedNotPricedInAnotherCurrency() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		// EST-15 has no ja_JP ItemDetails. Falling back to its en_US price would
		// sum dollars into a yen totalValue, so the line is rejected — in the
		// legacy, per-locale catalog queries meant a ja_JP shopper never saw it.
		mockMvc.perform(post("/api/orders")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderBody("ja_JP", "EST-15", 1)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownItemIsBadRequest() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		mockMvc.perform(post("/api/orders")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderBody("en_US", "EST-999", 1)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void emptyLinesIsBadRequest() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		mockMvc.perform(post("/api/orders")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderBody("en_US", null, 0)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void zeroQtyIsBadRequest() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		mockMvc.perform(post("/api/orders")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderBody("en_US", "EST-1", 0)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void placingAnOrderWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderBody("en_US", "EST-1", 1)))
				.andExpect(status().isUnauthorized());
	}

	/** Order body with one line (or none when itemId is null) and a fixed ship/bill contact. */
	private String orderBody(String locale, String itemId, int qty) {
		String lines = itemId == null ? "[]"
				: "[{\"itemId\":\"%s\",\"qty\":%d}]".formatted(itemId, qty);
		String contact = """
				{"familyName":"ABC","givenName":"XYZ",
				 "address":{"street":["1234 Anywhere Street"],"city":"Palo Alto","state":"CA","zipCode":"94303","country":"USA"},
				 "email":"aaa@bbb.ccc","phone":"555-555-5555"}""";
		return """
				{"locale":"%s","lines":%s,"shipTo":%s,"billTo":%s,
				 "creditCard":{"cardNumber":"123456789","cardType":"Meow Card","expiryDate":"04/04"}}"""
				.formatted(locale, lines, contact, contact);
	}

	private String loginAndGetToken(String username, String password) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("token").asText();
	}
}
