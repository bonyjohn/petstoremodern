package com.petstore.core.order.document;

/** Ship-to / bill-to party on an order (snapshot at checkout, not a customer reference). */
public record OrderContact(String familyName, String givenName, OrderAddress address, String email, String phone) {
}
