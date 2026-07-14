package com.petstore.core.catalog.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One legacy {@code <Product>} — {@code _id} is the legacy product id (e.g. {@code FI-SW-01}).
 * {@code ProductDetails} is migrated verbatim, per locale. Items reference this document
 * by {@code productId} rather than embedding it — the product has its own lifecycle and
 * carries data (description, image) that differs from its items' own.
 * Indexes (categoryId, text over details.*.name/description) are created explicitly
 * by the seeding step of the data migration.
 */
@Document("products")
public record ProductDocument(
		@Id String id,
		String categoryId,
		LocaleDetails details) {
}
