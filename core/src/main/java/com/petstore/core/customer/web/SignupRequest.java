package com.petstore.core.customer.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Minimal signup: legacy's signup flow collects the full contact-info form up front,
 * but the modern flow lets a customer fill in the rest later via {@code PUT /api/customers/me}.
 */
public record SignupRequest(
		@NotBlank String username,
		@NotBlank @Size(min = 4) String password,
		@NotBlank String familyName,
		@NotBlank String givenName,
		@NotBlank @Email String email) {
}
