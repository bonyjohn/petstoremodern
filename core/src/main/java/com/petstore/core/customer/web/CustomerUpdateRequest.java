package com.petstore.core.customer.web;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/customers/me}: replaces the account and profile in one go. */
public record CustomerUpdateRequest(
		@NotNull AccountDto account,
		@NotNull ProfileDto profile) {
}
