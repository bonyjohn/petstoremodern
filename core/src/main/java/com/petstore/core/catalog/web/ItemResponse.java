package com.petstore.core.catalog.web;

import java.math.BigDecimal;
import java.util.List;

/**
 * One sellable item, localized to the requested locale (falls back to en_US).
 * {@code name} comes from the item's joined product; the rest comes from the item itself.
 */
public record ItemResponse(
		String itemId,
		String productId,
		String name,
		String description,
		String image,
		BigDecimal listPrice,
		List<String> attributes) {
}
