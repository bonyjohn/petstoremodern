package com.petstore.core.order.web;

import java.time.Instant;

/** One audit-trail entry of an order's status history. */
public record StatusChangeResponse(String status, Instant at) {
}
