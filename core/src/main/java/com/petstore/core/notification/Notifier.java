package com.petstore.core.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.petstore.core.common.OrderStatusChangedEvent;

/**
 * Log-only stand-in for the legacy mail MDBs: when an order's status changes,
 * "send" the customer an email as a log line. The legacy's own SendMail flags
 * shipped as false, so log-only matches its default behavior. To send real
 * email, call EmailSender.send(...) in place of the log line below.
 */
@Component
public class Notifier {

	private static final Logger log = LoggerFactory.getLogger(Notifier.class);

	@EventListener
	public void onOrderStatusChanged(OrderStatusChangedEvent event) {
		if (event.email() == null) {
			log.info("Order {} is now {} — customer has no email on file, nothing to send",
					event.orderId(), event.status());
			return;
		}
		log.info("Email to {}: your order {} is now {}", event.email(), event.orderId(), event.status());
	}
}
