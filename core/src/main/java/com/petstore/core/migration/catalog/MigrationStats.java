package com.petstore.core.migration.catalog;

/**
 * Counts from parsing the legacy catalog XML into the document model: how many
 * of each level were parsed, and what got dropped along the way.
 */
public record MigrationStats(
		int categoriesConverted,
		int productsConverted,
		int itemsConverted,
		int itemsDroppedNoProduct,
		int itemLocalesDropped) {
}
