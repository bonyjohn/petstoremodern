package com.petstore.core.order.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One order — {@code _id} is a sequential order id (e.g. {@code 1001}) issued from the
 * {@code counters} collection. Replaces the legacy PurchaseOrder/LineItem/ContactInfo/
 * Address/CreditCard tables and the XML-over-JMS pipeline with a single document.
 * {@code statusHistory} is an audit trail of every transition with its timestamp —
 * something the legacy system never recorded.
 */
@Document("orders")
public record OrderDocument(
		@Id String id,
		String userId,
		String email,
		String locale,
		Instant orderDate,
		OrderStatus status,
		List<StatusChange> statusHistory,
		BigDecimal totalValue,
		OrderContact shipTo,
		OrderContact billTo,
		OrderCreditCard creditCard,
		List<OrderLine> lines) {
}
