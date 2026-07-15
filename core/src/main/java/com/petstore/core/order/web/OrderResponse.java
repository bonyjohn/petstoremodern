package com.petstore.core.order.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * An order as returned to its owner. Deliberately omits the credit card —
 * it's write-only through the API.
 */
public record OrderResponse(
		String orderId,
		String status,
		Instant orderDate,
		String locale,
		BigDecimal totalValue,
		List<OrderLineResponse> lines,
		List<StatusChangeResponse> statusHistory) {
}
