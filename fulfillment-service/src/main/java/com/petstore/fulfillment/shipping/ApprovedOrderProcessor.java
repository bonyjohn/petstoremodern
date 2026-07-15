package com.petstore.fulfillment.shipping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
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

/**
 * Ships an approved order: decrements inventory per line and reports the shipped
 * quantities back to core's internal callback. Safe under at-least-once delivery:
 * the decrement is bounded by the order's remaining unshipped quantity (which core
 * advances on every callback), and core's callback is itself qtyShipped-guarded —
 * so replaying an already-shipped order decrements nothing and changes nothing.
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

	public void process(Document order) {
		String orderId = order.getString("_id");
		List<Document> shipments = new ArrayList<>();

		for (Document line : order.getList("lines", Document.class)) {
			String itemId = line.getString("itemId");
			int qty = line.getInteger("qty", 0);
			int qtyShipped = line.getInteger("qtyShipped", 0);
			int shipped = reserve(itemId, qty, qtyShipped);
			if (shipped > 0) {
				shipments.add(new Document("itemId", itemId).append("qtyShipped", shipped));
			}
		}

		if (shipments.isEmpty()) {
			log.info("Order {}: nothing to ship (already shipped or no stock)", orderId);
			return;
		}

		restClient.post()
				.uri("/api/internal/orders/{id}/shipments", orderId)
				.header("X-Internal-Token", internalToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("shipments", shipments))
				.retrieve()
				.toBodilessEntity();

		log.info("Order {}: shipped {} line(s), core notified", orderId, shipments.size());
	}

	/**
	 * Atomically takes up to the line's unshipped remainder from stock. The
	 * conditional update (quantityOnHand >= take) means on-hand never goes
	 * negative even under concurrent processors; on a lost race, re-read and retry.
	 */
	private int reserve(String itemId, int qty, int qtyShipped) {
		for (int attempt = 0; attempt < 3; attempt++) {
			Document stock = mongoTemplate.findById(itemId, Document.class, "inventory");
			if (stock == null) {
				return 0;
			}
			int take = ShippingMath.quantityToShip(qty, qtyShipped, ((Number) stock.get("quantityOnHand")).longValue());
			if (take == 0) {
				return 0;
			}
			long modified = mongoTemplate.updateFirst(
					Query.query(Criteria.where("_id").is(itemId).and("quantityOnHand").gte(take)),
					new Update().inc("quantityOnHand", -take),
					"inventory").getModifiedCount();
			if (modified == 1) {
				return take;
			}
		}
		return 0;
	}
}
