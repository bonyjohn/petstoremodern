package com.petstore.core.order.document;

import java.math.BigDecimal;

/**
 * One line of an order. {@code unitPrice} is the server-side price at checkout
 * (client cart prices are untrusted); {@code qtyShipped} advances as the
 * fulfillment side ships, driving PARTIALLY_SHIPPED/COMPLETED.
 */
public record OrderLine(
		int lineNo,
		String itemId,
		String productId,
		String categoryId,
		int qty,
		BigDecimal unitPrice,
		int qtyShipped) {
}
