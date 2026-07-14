package com.petstore.core.catalog.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One legacy {@code <Item>} (the sellable unit) — {@code _id} is the legacy item id
 * (e.g. {@code EST-1}). {@code ItemDetails} is migrated verbatim, per locale; items
 * carry no name of their own in the legacy model — the name always comes from the
 * referenced product, joined at read time.
 * Indexes (productId) are created explicitly by the seeding step of the data migration.
 */
@Document("items")
public record ItemDocument(
		@Id String id,
		String productId,
		LocaleDetails details) {
}
