package com.petstore.core.catalog.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * First Testcontainers test: boots the real app against a throwaway MongoDB,
 * seeds it through the production {@code CatalogSeeder} ({@code petstore.seed=true}),
 * and characterizes the browse/search endpoints against DEMO_FLOW.md's browse flow.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "petstore.seed=true")
@AutoConfigureMockMvc
class CatalogControllerIntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void categoriesReturnsAllFive() throws Exception {
		mockMvc.perform(get("/api/catalog/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(5));
	}

	@Test
	void fishProductsIncludeAngelfishWithItsOwnImageAndDescription() throws Exception {
		mockMvc.perform(get("/api/catalog/categories/FISH/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.productId == 'FI-SW-01')].name").value("Angelfish"))
				.andExpect(jsonPath("$[?(@.productId == 'FI-SW-01')].image").value("fish1.jpg"))
				.andExpect(jsonPath("$[?(@.productId == 'FI-SW-01')].description")
						.value("Salt Water fish from Australia"));
	}

	@Test
	void productDetailForFiSw01() throws Exception {
		mockMvc.perform(get("/api/catalog/products/FI-SW-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productId").value("FI-SW-01"))
				.andExpect(jsonPath("$.categoryId").value("FISH"))
				.andExpect(jsonPath("$.name").value("Angelfish"))
				.andExpect(jsonPath("$.image").value("fish1.jpg"))
				.andExpect(jsonPath("$.description").value("Salt Water fish from Australia"));
	}

	@Test
	void angelfishItemsIncludeEst1NamedFromItsProductAt1650WithTheItemsOwnImage() throws Exception {
		mockMvc.perform(get("/api/catalog/products/FI-SW-01/items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.itemId == 'EST-1')].name").value("Angelfish"))
				.andExpect(jsonPath("$[?(@.itemId == 'EST-1')].listPrice").value(16.50))
				.andExpect(jsonPath("$[?(@.itemId == 'EST-1')].image").value("fish3.gif"));
	}

	@Test
	void itemDetailForEst1JoinsItsProductForTheName() throws Exception {
		mockMvc.perform(get("/api/catalog/items/EST-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.itemId").value("EST-1"))
				.andExpect(jsonPath("$.productId").value("FI-SW-01"))
				.andExpect(jsonPath("$.name").value("Angelfish"))
				.andExpect(jsonPath("$.listPrice").value(16.50));
	}

	@Test
	void searchAngelfishFindsProductFiSw01NotItems() throws Exception {
		mockMvc.perform(get("/api/catalog/search").param("q", "angelfish"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.productId == 'FI-SW-01')]").exists());
	}

	@Test
	void japaneseLocaleReturnsJapaneseNameForItemEst1() throws Exception {
		mockMvc.perform(get("/api/catalog/items/EST-1").param("locale", "ja_JP"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("エンゼルフィッシュ")));
	}

	@Test
	void japaneseLocaleReturnsJapanesePriceNotAFxConversion() throws Exception {
		mockMvc.perform(get("/api/catalog/items/EST-1").param("locale", "ja_JP"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.listPrice").value(1951));
	}

	@Test
	void defaultLocaleReturnsEnUsPriceForEst1() throws Exception {
		mockMvc.perform(get("/api/catalog/items/EST-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.listPrice").value(16.50));
	}

	@Test
	void itemWithNoJapaneseDetailFallsBackToTheWholeEnUsBlock() throws Exception {
		mockMvc.perform(get("/api/catalog/items/EST-15").param("locale", "ja_JP"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.listPrice").value(23.50))
				.andExpect(jsonPath("$.description").value("Great for reducing mouse populations"));
	}
}
