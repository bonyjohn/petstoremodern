package com.petstore.core.order.web;

/** Wire shape for {@link com.petstore.core.order.document.OrderCreditCard}. */
public record OrderCreditCardDto(String cardNumber, String cardType, String expiryDate) {
}
