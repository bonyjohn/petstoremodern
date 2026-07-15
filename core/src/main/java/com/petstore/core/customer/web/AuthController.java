package com.petstore.core.customer.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petstore.core.customer.service.CustomerService;

import jakarta.validation.Valid;

/** Signup and login (see DEMO_FLOW.md's registration/sign-in flow). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final CustomerService customerService;

	public AuthController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@PostMapping("/signup")
	public ResponseEntity<LoginResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(customerService.signup(request));
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return customerService.login(request);
	}
}
