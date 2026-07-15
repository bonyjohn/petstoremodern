package com.petstore.core.order.document;

import java.util.List;

/**
 * Postal address embedded in an order. Deliberately duplicates the customer
 * module's address shape rather than importing it: an order is a snapshot of
 * where it shipped, owned by this module — the modules stay independently
 * evolvable and the boundary is enforced by the architecture tests.
 */
public record OrderAddress(List<String> street, String city, String state, String zipCode, String country) {
}
