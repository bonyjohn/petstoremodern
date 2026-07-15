package com.petstore.core.order.document;

/** Lifecycle of an order, mirroring the legacy order-processing pipeline's states. */
public enum OrderStatus {
	PENDING,
	APPROVED,
	DENIED,
	PARTIALLY_SHIPPED,
	COMPLETED
}
