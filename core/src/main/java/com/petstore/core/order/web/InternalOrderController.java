package com.petstore.core.order.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.petstore.core.order.service.ShipmentService;

import jakarta.validation.Valid;

/**
 * Service-to-service callback for the fulfillment service. Authenticated by a
 * shared static token in {@code X-Internal-Token} rather than a user JWT — the
 * caller is a service, not a person. In production this would be mTLS or an
 * OAuth2 client-credentials token; a shared secret keeps local dev simple.
 */
@RestController
@RequestMapping("/api/internal/orders")
public class InternalOrderController {

	private final ShipmentService shipmentService;
	private final String internalToken;

	public InternalOrderController(ShipmentService shipmentService,
			@Value("${petstore.internal.token}") String internalToken) {
		this.shipmentService = shipmentService;
		this.internalToken = internalToken;
	}

	@PostMapping("/{id}/shipments")
	public void shipments(@PathVariable String id,
			@RequestHeader(name = "X-Internal-Token", required = false) String token,
			@Valid @RequestBody ShipmentRequest request) {
		// Constant-time comparison — a plain equals() leaks a timing oracle.
		if (token == null || !MessageDigest.isEqual(
				internalToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad internal token");
		}
		shipmentService.recordShipments(id, request.shipments());
	}
}
