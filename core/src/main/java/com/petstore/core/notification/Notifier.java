package com.petstore.core.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.petstore.core.common.OrderStatusChangedEvent;

/**
 * Log-only stand-in for the legacy MailOrderApprovalMDB: when an order's status
 * changes, "send" the customer an email as a log line.
 */
@Component
public class Notifier {

	private static final Logger log = LoggerFactory.getLogger(Notifier.class);

	@EventListener
	public void onOrderStatusChanged(OrderStatusChangedEvent event) {
		log.info("Email to {}: your order {} is now {}", event.email(), event.orderId(), event.status());
	}
}
