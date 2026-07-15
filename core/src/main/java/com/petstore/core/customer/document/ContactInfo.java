package com.petstore.core.customer.document;

/** The legacy {@code <ContactInfo>}: name, postal address, email, phone. */
public record ContactInfo(String familyName, String givenName, Address address, String email, String phone) {
}
