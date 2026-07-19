package com.petstore.fulfillment.shipping;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.petstore.fulfillment.inventory.InventoryDocument;

/**
 * Ships an approved order: decrements inventory per line and reports the shipped
 * quantities back to core's internal callback. Safe under at-least-once delivery:
 * the decrement is bounded by the order's remaining unshipped quantity (which core
 * advances on every callback), and core's callback is itself qtyShipped-guarded —
 * so replaying an already-shipped order decrements nothing and changes nothing.
 * If the callback itself fails (core unreachable), the decrement is compensated
 * back into stock before rethrowing, so periodic sweep retries don't leak
 * inventory while core is down.
 */
@Component
public class ApprovedOrderProcessor {

	private static final Logger log = LoggerFactory.getLogger(ApprovedOrderProcessor.class);

	private final MongoTemplate mongoTemplate;
	private final RestClient restClient;
	private final String internalToken;

	public ApprovedOrderProcessor(MongoTemplate mongoTemplate,
			@Value("${petstore.core.url}") String coreUrl,
			@Value("${petstore.internal.token}") String internalToken) {
		this.mongoTemplate = mongoTemplate;
		this.restClient = RestClient.builder().baseUrl(coreUrl).build();
		this.internalToken = internalToken;
	}

	public void process(ApprovedOrder order) {
		List<ShipmentCallback.ShipmentLine> shipments = new ArrayList<>();

		for (ApprovedOrder.Line line : order.lines()) {
			int shipped = reserve(line.itemId(), line.qty(), line.qtyShipped());
			if (shipped > 0) {
				shipments.add(new ShipmentCallback.ShipmentLine(line.itemId(), shipped));
			}
		}

		if (shipments.isEmpty()) {
			log.info("Order {}: nothing to ship (already shipped or no stock)", order.orderId());
			return;
		}

		try {
			restClient.post()
					.uri("/api/internal/orders/{id}/shipments", order.orderId())
					.header("X-Internal-Token", internalToken)
					.contentType(MediaType.APPLICATION_JSON)
					.body(new ShipmentCallback(shipments))
					.retrieve()
					.toBodilessEntity();
		} catch (RuntimeException e) {
			restock(order.orderId(), shipments);
			throw e;
		}

		log.info("Order {}: shipped {} line(s), core notified", order.orderId(), shipments.size());
	}

	private void restock(String orderId, List<ShipmentCallback.ShipmentLine> shipments) {
		for (ShipmentCallback.ShipmentLine shipment : shipments) {
			mongoTemplate.updateFirst(
					Query.query(Criteria.where("_id").is(shipment.itemId())),
					new Update().inc("quantityOnHand", shipment.qtyShipped()),
					InventoryDocument.class);
		}
		log.warn("Order {}: callback failed, returned {} line(s) to stock for retry", orderId, shipments.size());
	}

	private int reserve(String itemId, int qty, int qtyShipped) {
		for (int attempt = 0; attempt < 3; attempt++) {
			InventoryDocument stock = mongoTemplate.findById(itemId, InventoryDocument.class);
			if (stock == null) {
				return 0;
			}
			int take = ShippingMath.quantityToShip(qty, qtyShipped, stock.quantityOnHand());
			if (take == 0) {
				return 0;
			}
			long modified = mongoTemplate.updateFirst(
					Query.query(Criteria.where("_id").is(itemId).and("quantityOnHand").gte(take)),
					new Update().inc("quantityOnHand", -take),
					InventoryDocument.class).getModifiedCount();
			if (modified == 1) {
				return take;
			}
		}
		return 0;
	}
}
