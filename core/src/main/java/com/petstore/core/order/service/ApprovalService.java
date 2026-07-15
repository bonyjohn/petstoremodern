package com.petstore.core.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.event.EventListener;
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

	public ApprovalService(OrderRepository orderRepository, EventPublisher eventPublisher) {
		this.orderRepository = orderRepository;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * Auto-approval rule, verbatim from the legacy {@code PurchaseOrderMDB.canIApprove}:
	 * en_US orders strictly under $500 and ja_JP orders strictly under ¥50000 are
	 * approved automatically; every other locale (including zh_CN) always waits
	 * for the admin.
	 */
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

		List<StatusChange> history = new ArrayList<>(order.statusHistory());
		history.add(new StatusChange(next, Instant.now()));

		orderRepository.save(new OrderDocument(
				order.id(), order.userId(), order.email(), order.locale(), order.orderDate(),
				next, history, order.totalValue(), order.shipTo(), order.billTo(),
				order.creditCard(), order.lines()));

		eventPublisher.publish(new OrderStatusChangedEvent(order.id(), next.name(), order.email()));
	}
}
