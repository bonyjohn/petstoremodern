package com.petstore.core.migration.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.petstore.core.catalog.document.CategoryDocument;
import com.petstore.core.catalog.document.ItemDocument;
import com.petstore.core.catalog.document.ProductDocument;
import com.petstore.core.migration.catalog.LegacyCatalogParser.Result;

/**
 * Characterizes the legacy {@code Populate-UTF8.xml} -> Mongo document parsing against
 * the real seed data (no Mongo needed). Exact counts below were established by inspecting
 * the file: 5 categories, 16 products, 28 items (one item, EST-15, has no ja_JP ItemDetails).
 */
class LegacyCatalogParserTest {

	private static Result parseRealCatalog() {
		LegacyCatalogParser parser = new LegacyCatalogParser();
		try (InputStream xml = LegacyCatalogParserTest.class.getResourceAsStream("/migration/Populate-UTF8.xml")) {
			return parser.parse(xml);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void parsesExactCountsFromRealSeedData() {
		Result result = parseRealCatalog();

		assertThat(result.categories()).hasSize(5);
		assertThat(result.products()).hasSize(16);
		assertThat(result.items()).hasSize(28);
	}

	@Test
	void categoryIdsMatchLegacyIds() {
		Result result = parseRealCatalog();
		assertThat(result.categories())
				.extracting(CategoryDocument::id)
				.containsExactlyInAnyOrder("FISH", "DOGS", "REPTILES", "CATS", "BIRDS");
	}

	@Test
	void productFiSw01CarriesItsOwnVerbatimProductDetails() {
		Result result = parseRealCatalog();
		ProductDocument fiSw01 = findProduct(result, "FI-SW-01");

		assertThat(fiSw01.categoryId()).isEqualTo("FISH");
		assertThat(fiSw01.details().en_US().name()).isEqualTo("Angelfish");
		assertThat(fiSw01.details().en_US().image()).isEqualTo("fish1.jpg");
		assertThat(fiSw01.details().en_US().description()).isEqualTo("Salt Water fish from Australia");
		assertThat(fiSw01.details().ja_JP().name()).isEqualTo("エンゼルフィッシュ");
		assertThat(fiSw01.details().zh_CN().name()).isEqualTo("天使鱼");
	}

	@Test
	void itemEst1CarriesItsOwnVerbatimItemDetailsWithNoName() {
		Result result = parseRealCatalog();
		ItemDocument est1 = findItem(result, "EST-1");

		assertThat(est1.productId()).isEqualTo("FI-SW-01");
		assertThat(est1.details().en_US().name()).isNull();
		assertThat(est1.details().en_US().image()).isEqualTo("fish3.gif");
		assertThat(est1.details().en_US().description()).isEqualTo("Fresh Water fish from Japan");
		assertThat(est1.details().en_US().listPrice()).isEqualByComparingTo(new BigDecimal("16.50"));
		assertThat(est1.details().en_US().unitCost()).isEqualByComparingTo(new BigDecimal("10.00"));
		assertThat(est1.details().en_US().attributes()).containsExactly("Large", "Cuddly");
	}

	@Test
	void pricesAreMigratedVerbatimPerLocaleNotCanonicalized() {
		Result result = parseRealCatalog();
		ItemDocument est1 = findItem(result, "EST-1");

		assertThat(est1.details().ja_JP().listPrice()).isEqualByComparingTo(new BigDecimal("1951"));
		assertThat(est1.details().ja_JP().unitCost()).isEqualByComparingTo(new BigDecimal("1551"));
		assertThat(est1.details().ja_JP().attributes()).contains("大");
	}

	@Test
	void itemMissingALocaleIsDroppedForThatLocaleOnlyNotTheWholeItem() {
		Result result = parseRealCatalog();
		ItemDocument est15 = findItem(result, "EST-15");

		assertThat(est15.details().en_US()).isNotNull();
		assertThat(est15.details().zh_CN()).isNotNull();
		assertThat(est15.details().ja_JP()).isNull();
	}

	@Test
	void reportsWhatItDrops() {
		Result result = parseRealCatalog();
		MigrationStats stats = result.stats();

		assertThat(stats.categoriesConverted()).isEqualTo(5);
		assertThat(stats.productsConverted()).isEqualTo(16);
		assertThat(stats.itemsConverted()).isEqualTo(28);
		assertThat(stats.itemsDroppedNoProduct()).isZero();
		assertThat(stats.itemLocalesDropped()).isEqualTo(1); // EST-15's missing ja_JP
	}

	private ProductDocument findProduct(Result result, String productId) {
		Optional<ProductDocument> match = result.products().stream()
				.filter(p -> p.id().equals(productId))
				.findFirst();
		assertThat(match).as("product " + productId).isPresent();
		return match.get();
	}

	private ItemDocument findItem(Result result, String itemId) {
		Optional<ItemDocument> match = result.items().stream()
				.filter(i -> i.id().equals(itemId))
				.findFirst();
		assertThat(match).as("item " + itemId).isPresent();
		return match.get();
	}
}
