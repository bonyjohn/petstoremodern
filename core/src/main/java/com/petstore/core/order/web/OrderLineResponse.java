package com.petstore.core.order.web;

import java.math.BigDecimal;

/** One order line as priced by the server. */
public record OrderLineResponse(
		int lineNo,
		String itemId,
		String productId,
		String categoryId,
		int qty,
		BigDecimal unitPrice,
		int qtyShipped) {
}
