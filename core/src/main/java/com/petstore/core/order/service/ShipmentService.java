package com.petstore.core.order.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 * shipped line changes nothing.
 */
@Service
public class ShipmentService {

	private final OrderRepository orderRepository;
	private final EventPublisher eventPublisher;

	public ShipmentService(OrderRepository orderRepository, EventPublisher eventPublisher) {
		this.orderRepository = orderRepository;
		this.eventPublisher = eventPublisher;
	}

	public void recordShipments(String orderId, List<ShipmentLine> shipments) {
		OrderDocument order = orderRepository.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such order: " + orderId));

		Map<String, Integer> reportedByItem = shipments.stream()
				.collect(Collectors.toMap(ShipmentLine::itemId, ShipmentLine::qtyShipped, Integer::sum));

		List<OrderLine> lines = order.lines().stream()
				.map(line -> applyShipment(line, reportedByItem.getOrDefault(line.itemId(), 0)))
				.toList();

		OrderStatus target = statusFor(lines);
		// A replay that leaves the status where it is (e.g. still PARTIALLY_SHIPPED)
		// is a no-op, not an illegal transition.
		OrderStatus next = target == order.status() ? order.status()
				: OrderTransitions.next(order.status(), target);

		List<StatusChange> history = order.statusHistory();
		boolean changed = next != order.status();
		if (changed) {
			history = new ArrayList<>(history);
			history.add(new StatusChange(next, Instant.now()));
		}

		orderRepository.save(new OrderDocument(
				order.id(), order.userId(), order.email(), order.locale(), order.orderDate(),
				next, history, order.totalValue(), order.shipTo(), order.billTo(),
				order.creditCard(), lines));

		if (changed) {
			eventPublisher.publish(new OrderStatusChangedEvent(order.id(), next.name(), order.email()));
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
