package com.petstore.fulfillment.shipping;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mongodb.client.model.Filters;

/**
 * Reconciliation sweep: finds every order that should ship but hasn't —
 * status APPROVED or PARTIALLY_SHIPPED with any line still under-shipped —
 * and feeds it to the same idempotent {@link ApprovedOrderProcessor} the
 * change stream uses. The change stream is a latency optimization; this sweep
 * is the delivery guarantee. It runs on startup/lease-acquisition (orders
 * approved before this service ever started), after a lost resume token
 * (orders approved while the checkpoint was dead), and periodically — the
 * periodic run also restores the legacy backorder behavior DEMO_FLOW.md
 * documents: an order stalled on empty stock ships within one sweep interval
 * of restocking. A sweep can re-ship an order whose callback is still in
 * flight — the same bounded window as a crash between decrement and callback,
 * capped by the line's remaining quantity.
 */
@Component
public class ReconciliationSweep {

	private static final Logger log = LoggerFactory.getLogger(ReconciliationSweep.class);

	private final MongoTemplate mongoTemplate;
	private final ApprovedOrderProcessor processor;
	private final ConsumerLease lease;

	public ReconciliationSweep(MongoTemplate mongoTemplate, ApprovedOrderProcessor processor, ConsumerLease lease) {
		this.mongoTemplate = mongoTemplate;
		this.processor = processor;
		this.lease = lease;
	}

	/** Ships every shippable-but-unshipped order. Safe to run any time; every step is idempotent. */
	public void sweep() {
		Bson shippableButUnshipped = Filters.and(
				Filters.in("status", List.of("APPROVED", "PARTIALLY_SHIPPED")),
				Filters.expr(new Document("$anyElementTrue", new Document("$map",
						new Document("input", "$lines")
								.append("as", "line")
								.append("in", new Document("$lt", List.of("$$line.qtyShipped", "$$line.qty")))))));

		for (Document order : mongoTemplate.getCollection("orders").find(shippableButUnshipped)) {
			try {
				processor.process(order);
			} catch (Exception e) {
				// One unshippable order must not starve the rest; it is retried next sweep.
				log.warn("Sweep failed for order {}", order.getString("_id"), e);
			}
		}
	}

	/** Periodic pass, only on the instance holding the consumer lease. */
	@Scheduled(fixedDelayString = "${petstore.fulfillment.sweep-interval}")
	void scheduledSweep() {
		if (lease.held()) {
			sweep();
		}
	}
}
