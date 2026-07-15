package com.petstore.core.customer.document;

/** The legacy {@code <Account>} aggregate: contact info plus a single credit card on file. */
public record Account(ContactInfo contactInfo, CreditCard creditCard) {
}
