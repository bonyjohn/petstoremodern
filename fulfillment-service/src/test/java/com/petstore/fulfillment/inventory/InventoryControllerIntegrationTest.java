package com.petstore.fulfillment.inventory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Admin-only inventory REST: role enforcement and the read/update roundtrip. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class InventoryControllerIntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8").withReplicaSet();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InventoryRepository inventoryRepository;

	@Test
	void adminReadsAndUpdatesStock() throws Exception {
		inventoryRepository.save(new InventoryDocument("EST-1", 10000));

		mockMvc.perform(get("/api/inventory")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == 'EST-1')].quantityOnHand").value(10000));

		mockMvc.perform(put("/api/inventory/EST-1")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"quantityOnHand\":250}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantityOnHand").value(250));
	}

	@Test
	void plainUsersAreForbidden() throws Exception {
		mockMvc.perform(get("/api/inventory")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void anonymousIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/inventory")).andExpect(status().isUnauthorized());
	}
}
