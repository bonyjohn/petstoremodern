package com.petstore.core.customer.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record SignupRequest(
		@NotBlank String username,
		@NotBlank @Size(min = 4) String password,
		@NotBlank String familyName,
		@NotBlank String givenName,
		@NotBlank @Email String email) {
}
