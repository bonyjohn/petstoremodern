package com.petstore.core.customer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.petstore.core.customer.repository.CustomerRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Boots the real app against a throwaway MongoDB, seeds it through the production
 * {@code CatalogSeeder}/{@code CustomerSeeder} ({@code petstore.seed=true}), and
 * characterizes the auth + customer-profile flows against DEMO_FLOW.md's sign-in flow.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "petstore.seed=true")
@AutoConfigureMockMvc
class CustomerAuthIntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CustomerRepository customerRepository;

	@Test
	void loginAsMigratedJ2eeSucceedsAndTheTokenReadsBackTheLegacyContactInfo() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		mockMvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("j2ee"))
				.andExpect(jsonPath("$.account.contactInfo.familyName").value("ABC"))
				.andExpect(jsonPath("$.account.contactInfo.givenName").value("XYZ"))
				.andExpect(jsonPath("$.account.contactInfo.email").value("aaa@bbb.ccc"))
				.andExpect(jsonPath("$.account.contactInfo.address.street[0]").value("1234 Anywhere Street"))
				.andExpect(jsonPath("$.account.contactInfo.address.street[1]").value("Unit 555"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void cardNumberIsMaskedInResponsesAndSavingTheMaskBackKeepsTheStoredNumber() throws Exception {
		String token = loginAndGetToken("j2ee", "j2ee");

		// The API serves a masked PAN...
		String me = mockMvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.account.creditCard.cardNumber").value("**** 6789"))
				.andReturn().getResponse().getContentAsString();

		// ...and round-tripping it through PUT (the account form's save) must
		// retain the stored number, not overwrite the card with the mask.
		var meJson = objectMapper.readTree(me);
		String update = "{\"account\":%s,\"profile\":%s}"
				.formatted(meJson.get("account").toString(), meJson.get("profile").toString());
		mockMvc.perform(put("/api/customers/me")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(update))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.account.creditCard.cardNumber").value("**** 6789"));

		assertThat(customerRepository.findById("j2ee").orElseThrow().account().creditCard().cardNumber())
				.isEqualTo("123456789"); // stored value intact, never the mask
	}

	@Test
	void loginWithWrongPasswordIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest("j2ee", "wrong-password"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meWithoutATokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/customers/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void signupThenLoginThenMeRoundTrips() throws Exception {
		SignupRequest signup = new SignupRequest("newshopper", "sekret1", "Doe", "Jane", "jane@example.com");
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(signup)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("newshopper"))
				.andExpect(jsonPath("$.roles[0]").value("USER"));

		String token = loginAndGetToken("newshopper", "sekret1");

		mockMvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("newshopper"))
				.andExpect(jsonPath("$.account.contactInfo.familyName").value("Doe"))
				.andExpect(jsonPath("$.account.contactInfo.email").value("jane@example.com"));
	}

	@Test
	void duplicateSignupIsConflict() throws Exception {
		SignupRequest signup = new SignupRequest("dupeshopper", "sekret1", "A", "B", "a@b.com");
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(signup)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(signup)))
				.andExpect(status().isConflict());
	}

	@Test
	void catalogEndpointsRemainPublicWithoutAToken() throws Exception {
		mockMvc.perform(get("/api/catalog/categories")).andExpect(status().isOk());
	}

	@Test
	void adminUserHasRoleAdmin() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("ADMIN")));
	}

	private String loginAndGetToken(String username, String password) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("token").asText();
	}
}
