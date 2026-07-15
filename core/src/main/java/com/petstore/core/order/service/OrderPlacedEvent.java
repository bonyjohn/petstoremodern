package com.petstore.core.order.service;

import java.math.BigDecimal;

/** A new order was inserted as PENDING; carries what the approval rule needs. */
public record OrderPlacedEvent(String orderId, String locale, BigDecimal totalValue) {
}
