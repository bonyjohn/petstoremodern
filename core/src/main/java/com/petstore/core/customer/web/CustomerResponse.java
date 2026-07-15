package com.petstore.core.customer.web;

import java.util.List;

/** The authenticated customer's own account and profile. Never carries {@code passwordHash}. */
public record CustomerResponse(String username, List<String> roles, AccountDto account, ProfileDto profile) {
}
