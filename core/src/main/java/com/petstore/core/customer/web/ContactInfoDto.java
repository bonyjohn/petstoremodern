package com.petstore.core.customer.web;

/** Wire shape for {@link com.petstore.core.customer.document.ContactInfo}. */
public record ContactInfoDto(String familyName, String givenName, AddressDto address, String email, String phone) {
}
