package com.petstore.core.catalog.web;

/** One product, localized to the requested locale (falls back to en_US) — the product page header. */
public record ProductResponse(String productId, String categoryId, String name, String image, String description) {
}
