package com.petstore.core.order.web;

/** Wire shape for {@link com.petstore.core.order.document.OrderContact}. */
public record OrderContactDto(String familyName, String givenName, OrderAddressDto address, String email, String phone) {
}
