package com.petstore.core.order.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.petstore.core.order.document.OrderStatus;
import com.petstore.core.order.service.ApprovalService;
import com.petstore.core.order.service.OrderService;

/**
 * The admin's order queue (the legacy admin webapp's approve/deny screen).
 * ROLE_ADMIN is enforced for all of {@code /api/admin/**} in the security config.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

	private final OrderService orderService;
	private final ApprovalService approvalService;

	public AdminOrderController(OrderService orderService, ApprovalService approvalService) {
		this.orderService = orderService;
		this.approvalService = approvalService;
	}

	@GetMapping
	public List<AdminOrderResponse> orders(@RequestParam(required = false) OrderStatus status) {
		return orderService.listForAdmin(status);
	}

	@PostMapping("/{id}/approve")
	public void approve(@PathVariable String id) {
		transition(() -> approvalService.approve(id));
	}

	@PostMapping("/{id}/deny")
	public void deny(@PathVariable String id) {
		transition(() -> approvalService.deny(id));
	}

	/** An illegal transition (e.g. approving an already-approved order) is the caller's error, not a 500. */
	private void transition(Runnable action) {
		try {
			action.run();
		} catch (IllegalStateException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
		}
	}
}
