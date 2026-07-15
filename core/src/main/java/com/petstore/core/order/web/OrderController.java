package com.petstore.core.order.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petstore.core.order.service.OrderService;

import jakarta.validation.Valid;

/** Checkout and order lookup for the authenticated shopper (see DEMO_FLOW.md's checkout flow). */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> place(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PlaceOrderRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(orderService.place(jwt.getSubject(), request));
	}

	@GetMapping
	public List<OrderResponse> myOrders(@AuthenticationPrincipal Jwt jwt) {
		return orderService.listOwn(jwt.getSubject());
	}

	@GetMapping("/{id}")
	public ResponseEntity<OrderResponse> order(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
		boolean admin = isAdmin(jwt);
		return orderService.getForUser(id, jwt.getSubject(), admin)
				.map(ResponseEntity::ok)
				// Someone else's order id reads as not-found, never as forbidden — the
				// response must not confirm the id exists.
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	private boolean isAdmin(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList("roles");
		return roles != null && roles.contains("ADMIN");
	}
}
