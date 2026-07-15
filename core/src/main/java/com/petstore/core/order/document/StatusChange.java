package com.petstore.core.order.document;

import java.time.Instant;

/** One entry in an order's status audit trail. */
public record StatusChange(OrderStatus status, Instant at) {
}
