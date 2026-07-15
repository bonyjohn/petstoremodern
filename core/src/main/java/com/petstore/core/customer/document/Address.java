package com.petstore.core.customer.document;

import java.util.List;

/**
 * The legacy {@code <Address>}. {@code street} is a list because the legacy schema
 * allows one or two {@code <StreetName>} lines per customer.
 */
public record Address(List<String> street, String city, String state, String zipCode, String country) {
}
