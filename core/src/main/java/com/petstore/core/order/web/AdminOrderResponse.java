package com.petstore.core.order.web;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the admin's order list — includes the owner, which {@code OrderResponse} never does. */
public record AdminOrderResponse(
		String orderId,
		String userId,
		Instant orderDate,
		String locale,
		BigDecimal totalValue,
		String status) {
}
