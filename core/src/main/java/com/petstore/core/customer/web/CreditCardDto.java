package com.petstore.core.customer.web;

/** Wire shape for {@link com.petstore.core.customer.document.CreditCard}. */
public record CreditCardDto(String cardNumber, String cardType, String expiryDate) {
}
