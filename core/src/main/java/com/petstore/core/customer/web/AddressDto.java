package com.petstore.core.customer.web;

import java.util.List;

/** Wire shape for {@link com.petstore.core.customer.document.Address}. */
public record AddressDto(List<String> street, String city, String state, String zipCode, String country) {
}
