package com.petstore.core.migration.customer;

/**
 * Counts from converting legacy Users+Customers into {@code CustomerDocument}s: how many
 * joined cleanly, and how many customers had no matching user (and so no password to hash).
 */
public record CustomerMigrationStats(int customersConverted, int customersDroppedNoUser) {
}
