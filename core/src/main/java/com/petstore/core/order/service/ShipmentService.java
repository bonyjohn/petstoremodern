package com.petstore.core.order.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.petstore.core.common.EventPublisher;
import com.petstore.core.common.OrderStatusChangedEvent;
import com.petstore.core.order.document.OrderDocument;
import com.petstore.core.order.document.OrderLine;
import com.petstore.core.order.document.OrderStatus;
import com.petstore.core.order.document.StatusChange;
import com.petstore.core.order.repository.OrderRepository;
import com.petstore.core.order.web.ShipmentLine;

/**
 * Applies shipment callbacks from the fulfillment service. Guarded per line —
 * {@code qtyShipped} never exceeds {@code qty} — so the at-least-once delivery
 * of the change-stream pipeline is safe: replaying a callback for a fully
 * shipped line changes nothing. The write is a conditional update with the
 * read status as its precondition, so a concurrent transition can't be
 * silently overwritten; on a lost race we re-read once and re-apply.
 */
@Service
public class ShipmentService {

	private final OrderRepository orderRepository;
	private final EventPublisher eventPublisher;
	private final MongoTemplate mongoTemplate;

	public ShipmentService(OrderRepository orderRepository, EventPublisher eventPublisher,
			MongoTemplate mongoTemplate) {
		this.orderRepository = orderRepository;
		this.eventPublisher = eventPublisher;
		this.mongoTemplate = mongoTemplate;
	}

	public void recordShipments(String orderId, List<ShipmentLine> shipments) {
		Map<String, Integer> reportedByItem = shipments.stream()
				.collect(Collectors.toMap(ShipmentLine::itemId, ShipmentLine::qtyShipped, Integer::sum));

		for (int attempt = 0; attempt < 2; attempt++) {
			OrderDocument order = orderRepository.findById(orderId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such order: " + orderId));

			List<OrderLine> lines = order.lines().stream()
					.map(line -> applyShipment(line, reportedByItem.getOrDefault(line.itemId(), 0)))
					.toList();

			OrderStatus target = statusFor(lines);
			boolean statusChanged = target != order.status();
			if (!statusChanged && lines.equals(order.lines())) {
				return; 
			}
			if (statusChanged) {
				OrderTransitions.next(order.status(), target); 
			}

			Update update = new Update().set("lines", lines).set("status", target);
			if (statusChanged) {
				update.push("statusHistory", new StatusChange(target, Instant.now()));
			}
			long modified = mongoTemplate.updateFirst(
					Query.query(Criteria.where("_id").is(orderId).and("status").is(order.status())),
					update,
					OrderDocument.class).getModifiedCount();

			if (modified == 1) {
				if (statusChanged) {
					eventPublisher.publish(new OrderStatusChangedEvent(order.id(), target.name(), order.email()));
				}
				return;
			}
		}
	}

	private OrderLine applyShipment(OrderLine line, int reported) {
		int qtyShipped = Math.min(line.qty(), line.qtyShipped() + reported);
		return new OrderLine(line.lineNo(), line.itemId(), line.productId(), line.categoryId(),
				line.qty(), line.unitPrice(), qtyShipped);
	}

	private OrderStatus statusFor(List<OrderLine> lines) {
		boolean allShipped = lines.stream().allMatch(line -> line.qtyShipped() >= line.qty());
		if (allShipped) {
			return OrderStatus.COMPLETED;
		}
		boolean anyShipped = lines.stream().anyMatch(line -> line.qtyShipped() > 0);
		return anyShipped ? OrderStatus.PARTIALLY_SHIPPED : OrderStatus.APPROVED;
	}
}
