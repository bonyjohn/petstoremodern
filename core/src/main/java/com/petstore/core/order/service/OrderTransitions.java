package com.petstore.core.order.service;

import com.petstore.core.order.document.OrderStatus;

/**
 * The order lifecycle as pure functions: PENDING can be approved or denied,
 * an approved order ships (partially, then fully) to COMPLETED, and
 * DENIED/COMPLETED are terminal. Replaces transition logic the legacy pipeline
 * scattered across MDBs with one testable table.
 */
public final class OrderTransitions {

	private OrderTransitions() {
	}

	public static boolean isAllowed(OrderStatus current, OrderStatus target) {
		return switch (current) {
			case PENDING -> target == OrderStatus.APPROVED || target == OrderStatus.DENIED;
			case APPROVED -> target == OrderStatus.PARTIALLY_SHIPPED || target == OrderStatus.COMPLETED;
			case PARTIALLY_SHIPPED -> target == OrderStatus.COMPLETED;
			case DENIED, COMPLETED -> false;
		};
	}

	/** Returns the target status, or throws if the transition is not allowed. */
	public static OrderStatus next(OrderStatus current, OrderStatus target) {
		if (!isAllowed(current, target)) {
			throw new IllegalStateException("Illegal order transition: " + current + " -> " + target);
		}
		return target;
	}
}
