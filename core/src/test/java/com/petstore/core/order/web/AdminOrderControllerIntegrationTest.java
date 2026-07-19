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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;

/**
 * Characterizes the admin order queue (the legacy admin webapp's approve/deny
 * screen): only admins may use it, and decisions go through the state machine.
 * Ordered: later methods act on orders placed by earlier ones.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "petstore.seed=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class AdminOrderControllerIntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@Order(1)
	void pendingOrdersShowUpInTheAdminQueueWithTheirOwner() throws Exception {
		String j2ee = loginAndGetToken("j2ee", "j2ee");
		placeBigPendingOrder(j2ee); 
		placeBigPendingOrder(j2ee);

		String admin = loginAndGetToken("admin", "admin123");
		mockMvc.perform(get("/api/admin/orders").param("status", "PENDING")
						.header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.orderId == '1001')].userId").value("j2ee"))
				.andExpect(jsonPath("$[?(@.orderId == '1001')].status").value("PENDING"));
	}

	@Test
	@Order(2)
	void plainUsersAreForbiddenFromTheAdminQueue() throws Exception {
		String j2ee = loginAndGetToken("j2ee", "j2ee");

		mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + j2ee))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/orders/1001/approve").header("Authorization", "Bearer " + j2ee))
				.andExpect(status().isForbidden());
	}

	@Test
	@Order(3)
	void adminApprovesAPendingOrder() throws Exception {
		String admin = loginAndGetToken("admin", "admin123");

		mockMvc.perform(post("/api/admin/orders/1001/approve").header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/orders/1001").header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.statusHistory[1].status").value("APPROVED"));
	}

	@Test
	@Order(4)
	void approvingAnAlreadyApprovedOrderIsAConflictNotAServerError() throws Exception {
		String admin = loginAndGetToken("admin", "admin123");

		mockMvc.perform(post("/api/admin/orders/1001/approve").header("Authorization", "Bearer " + admin))
				.andExpect(status().isConflict());
	}

	@Test
	@Order(5)
	void adminDeniesAPendingOrderAndTheQueueEmpties() throws Exception {
		String admin = loginAndGetToken("admin", "admin123");

		mockMvc.perform(post("/api/admin/orders/1002/deny").header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/orders/1002").header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DENIED"));

		mockMvc.perform(get("/api/admin/orders").param("status", "PENDING")
						.header("Authorization", "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	private void placeBigPendingOrder(String token) throws Exception {
		String contact = """
				{"familyName":"ABC","givenName":"XYZ",
				 "address":{"street":["1234 Anywhere Street"],"city":"Palo Alto","state":"CA","zipCode":"94303","country":"USA"},
				 "email":"aaa@bbb.ccc","phone":"555-555-5555"}""";
		String body = """
				{"locale":"en_US","lines":[{"itemId":"EST-6","qty":28}],"shipTo":%s,"billTo":%s,
				 "creditCard":{"cardNumber":"123456789","cardType":"Meow Card","expiryDate":"04/04"}}"""
				.formatted(contact, contact);
		mockMvc.perform(post("/api/orders")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));
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
