package com.petstore.core.order.web;

import java.util.List;

/** Wire shape for {@link com.petstore.core.order.document.OrderAddress}. */
public record OrderAddressDto(List<String> street, String city, String state, String zipCode, String country) {
}
