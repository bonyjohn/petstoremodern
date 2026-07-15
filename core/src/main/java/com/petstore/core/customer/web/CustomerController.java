package com.petstore.core.customer.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petstore.core.customer.service.CustomerService;

import jakarta.validation.Valid;

/** The authenticated customer's own account and profile. Identity comes from the JWT subject. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@GetMapping("/me")
	public CustomerResponse me(@AuthenticationPrincipal Jwt jwt) {
		return customerService.getMe(jwt.getSubject());
	}

	@PutMapping("/me")
	public CustomerResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerUpdateRequest request) {
		return customerService.updateMe(jwt.getSubject(), request);
	}
}
