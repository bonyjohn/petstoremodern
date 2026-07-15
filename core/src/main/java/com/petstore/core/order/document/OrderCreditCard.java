package com.petstore.core.order.document;

/** Card the order was placed with (snapshot at checkout, module-local like the addresses). */
public record OrderCreditCard(String cardNumber, String cardType, String expiryDate) {
}
