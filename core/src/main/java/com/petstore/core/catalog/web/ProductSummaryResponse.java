package com.petstore.core.catalog.web;

/** One product, localized to the requested locale (falls back to en_US) — a category page's or search result's product list. */
public record ProductSummaryResponse(String productId, String name, String image, String description) {
}
