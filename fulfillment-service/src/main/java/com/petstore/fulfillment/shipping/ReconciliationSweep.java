package com.petstore.fulfillment.shipping;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

	/** Status shippable AND any line with qtyShipped < qty. Static, so plain JSON — paste into mongosh to debug. */
	private static final Document SHIPPABLE_BUT_UNSHIPPED = Document.parse("""
			{
			  "status": { "$in": ["APPROVED", "PARTIALLY_SHIPPED"] },
			  "$expr": {
			    "$anyElementTrue": {
			      "$map": {
			        "input": "$lines",
			        "as": "line",
			        "in": { "$lt": ["$$line.qtyShipped", "$$line.qty"] }
			      }
			    }
			  }
			}
			""");

	private final MongoTemplate mongoTemplate;
	private final ApprovedOrderProcessor processor;
	private final ConsumerLease lease;

	public ReconciliationSweep(MongoTemplate mongoTemplate, ApprovedOrderProcessor processor, ConsumerLease lease) {
		this.mongoTemplate = mongoTemplate;
		this.processor = processor;
		this.lease = lease;
	}

	public void sweep() {
		for (Document order : mongoTemplate.getCollection("orders").find(SHIPPABLE_BUT_UNSHIPPED)) {
			try {
				processor.process(ApprovedOrder.from(order));
			} catch (Exception e) {
				log.warn("Sweep failed for order {}", order.getString("_id"), e);
			}
		}
	}

	@Scheduled(fixedDelayString = "${petstore.fulfillment.sweep-interval}")
	void scheduledSweep() {
		if (lease.held()) {
			sweep();
		}
	}
}
