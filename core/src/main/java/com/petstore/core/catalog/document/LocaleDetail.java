package com.petstore.core.catalog.document;

import java.math.BigDecimal;
import java.util.List;

/**
 * A category or product's localized name/image/description. For a product's item, also
 * carries that locale's own price/cost/attributes — the legacy catalog prices each item
 * independently per locale; categories leave these null.
 */
public record LocaleDetail(
		String name,
		String description,
		String image,
		BigDecimal listPrice,
		BigDecimal unitCost,
		List<String> attributes) {
}
