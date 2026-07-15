package com.petstore.core.customer.document;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One shopper — {@code _id} is the legacy user id (e.g. {@code j2ee}). Replaces the
 * legacy customer aggregate, which was shredded across six entity beans/tables
 * (User, Account, Profile, ContactInfo, CreditCard, Address), with a single document.
 * {@code passwordHash} is BCrypt and must never be serialized out of any API response.
 */
@Document("customers")
public record CustomerDocument(
		@Id String id,
		String passwordHash,
		List<String> roles,
		Account account,
		Profile profile) {
}
