package com.petstore.core.customer.web;

/** Wire shape for {@link com.petstore.core.customer.document.Account}. */
public record AccountDto(ContactInfoDto contactInfo, CreditCardDto creditCard) {
}
