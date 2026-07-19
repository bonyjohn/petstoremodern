package com.petstore.core.order.service;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.petstore.core.common.EventPublisher;
import com.petstore.core.common.OrderStatusChangedEvent;
import com.petstore.core.order.document.OrderDocument;
import com.petstore.core.order.document.OrderStatus;
import com.petstore.core.order.document.StatusChange;
import com.petstore.core.order.repository.OrderRepository;

/**
 * Approves or denies orders. Auto-approval runs synchronously on
 * {@link OrderPlacedEvent}; orders it can't approve stay PENDING for a human
 * admin to decide via {@link #approve}/{@link #deny}.
 */
@Service
public class ApprovalService {

	private static final BigDecimal EN_US_LIMIT = new BigDecimal(500);
	private static final BigDecimal JA_JP_LIMIT = new BigDecimal(50000);

	private final OrderRepository orderRepository;
	private final EventPublisher eventPublisher;
	private final MongoTemplate mongoTemplate;

	public ApprovalService(OrderRepository orderRepository, EventPublisher eventPublisher,
			MongoTemplate mongoTemplate) {
		this.orderRepository = orderRepository;
		this.eventPublisher = eventPublisher;
		this.mongoTemplate = mongoTemplate;
	}

	static boolean canIApprove(String locale, BigDecimal totalValue) {
		return switch (locale) {
		case "en_US" -> totalValue.compareTo(EN_US_LIMIT) < 0;
		case "ja_JP" -> totalValue.compareTo(JA_JP_LIMIT) < 0;
		default -> false;
		};
	}

	@EventListener
	public void onOrderPlaced(OrderPlacedEvent event) {
		if (canIApprove(event.locale(), event.totalValue())) {
			approve(event.orderId());
		}
	}

	public void approve(String orderId) {
		transition(orderId, OrderStatus.APPROVED);
	}

	public void deny(String orderId) {
		transition(orderId, OrderStatus.DENIED);
	}

	private void transition(String orderId, OrderStatus target) {
		OrderDocument order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalStateException("No such order: " + orderId));
		OrderStatus next = OrderTransitions.next(order.status(), target);

		long modified = mongoTemplate
				.updateFirst(Query.query(Criteria.where("_id").is(orderId).and("status").is(order.status())),
						new Update().set("status", next).push("statusHistory", new StatusChange(next, Instant.now())),
						OrderDocument.class)
				.getModifiedCount();
		if (modified == 0) {
			throw new IllegalStateException(
					"Order " + orderId + " was transitioned concurrently; expected status " + order.status());
		}

		eventPublisher.publish(new OrderStatusChangedEvent(order.id(), next.name(), order.email()));
	}
}
