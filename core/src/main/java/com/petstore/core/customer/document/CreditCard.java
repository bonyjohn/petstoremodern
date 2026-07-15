package com.petstore.core.customer.document;

/** The legacy {@code <CreditCard>} on file for the customer. */
public record CreditCard(String cardNumber, String cardType, String expiryDate) {
}
