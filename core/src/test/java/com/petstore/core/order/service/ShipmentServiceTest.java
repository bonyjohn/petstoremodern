package com.petstore.core.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * Unit-level characterization of the shipment guard: qtyShipped never exceeds
 * qty, replays are silent no-ops, and status follows the shipped quantities
 * through the state machine.
 */
class ShipmentServiceTest {

	private OrderRepository orderRepository;
	private EventPublisher eventPublisher;
	private ShipmentService shipmentService;

	@BeforeEach
	void setUp() {
		orderRepository = mock(OrderRepository.class);
		eventPublisher = mock(EventPublisher.class);
		shipmentService = new ShipmentService(orderRepository, eventPublisher);
	}

	@Test
	void partialShipmentMovesToPartiallyShippedAndPublishes() {
		givenOrder(approvedOrder(2, 0));

		shipmentService.recordShipments("1001", List.of(new ShipmentLine("EST-1", 1)));

		OrderDocument saved = savedOrder();
		assertThat(saved.status()).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);
		assertThat(saved.lines().get(0).qtyShipped()).isEqualTo(1);
		assertThat(saved.statusHistory()).hasSize(3); // PENDING, APPROVED, PARTIALLY_SHIPPED
		verify(eventPublisher).publish(new OrderStatusChangedEvent("1001", "PARTIALLY_SHIPPED", "aaa@bbb.ccc"));
	}

	@Test
	void shippingEverythingCompletesTheOrder() {
		givenOrder(approvedOrder(2, 0));

		shipmentService.recordShipments("1001", List.of(new ShipmentLine("EST-1", 2)));

		OrderDocument saved = savedOrder();
		assertThat(saved.status()).isEqualTo(OrderStatus.COMPLETED);
		assertThat(saved.lines().get(0).qtyShipped()).isEqualTo(2);
		verify(eventPublisher).publish(new OrderStatusChangedEvent("1001", "COMPLETED", "aaa@bbb.ccc"));
	}

	@Test
	void replayOnAFullyShippedLineIsANoOpAndPublishesNothing() {
		OrderDocument completed = order(OrderStatus.COMPLETED, 2, 2,
				List.of(new StatusChange(OrderStatus.PENDING, Instant.now()),
						new StatusChange(OrderStatus.APPROVED, Instant.now()),
						new StatusChange(OrderStatus.COMPLETED, Instant.now())));
		givenOrder(completed);

		shipmentService.recordShipments("1001", List.of(new ShipmentLine("EST-1", 2)));

		OrderDocument saved = savedOrder();
		assertThat(saved.status()).isEqualTo(OrderStatus.COMPLETED);
		assertThat(saved.lines().get(0).qtyShipped()).isEqualTo(2); // unchanged, not 4
		assertThat(saved.statusHistory()).hasSize(3); // no extra audit entry
		verify(eventPublisher, never()).publish(any());
	}

	@Test
	void overReportingClampsAtTheOrderedQuantity() {
		givenOrder(approvedOrder(2, 0));

		shipmentService.recordShipments("1001", List.of(new ShipmentLine("EST-1", 5)));

		OrderDocument saved = savedOrder();
		assertThat(saved.lines().get(0).qtyShipped()).isEqualTo(2); // min(qty, 0 + 5)
		assertThat(saved.status()).isEqualTo(OrderStatus.COMPLETED);
	}

	@Test
	void unknownOrderIsNotFound() {
		when(orderRepository.findById("nope")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> shipmentService.recordShipments("nope", List.of(new ShipmentLine("EST-1", 1))))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404");
	}

	private void givenOrder(OrderDocument order) {
		when(orderRepository.findById("1001")).thenReturn(Optional.of(order));
	}

	private OrderDocument savedOrder() {
		ArgumentCaptor<OrderDocument> captor = ArgumentCaptor.forClass(OrderDocument.class);
		verify(orderRepository).save(captor.capture());
		return captor.getValue();
	}

	private OrderDocument approvedOrder(int qty, int qtyShipped) {
		return order(OrderStatus.APPROVED, qty, qtyShipped,
				List.of(new StatusChange(OrderStatus.PENDING, Instant.now()),
						new StatusChange(OrderStatus.APPROVED, Instant.now())));
	}

	private OrderDocument order(OrderStatus status, int qty, int qtyShipped, List<StatusChange> history) {
		OrderLine line = new OrderLine(1, "EST-1", "FI-SW-01", "FISH", qty, new BigDecimal("16.50"), qtyShipped);
		return new OrderDocument("1001", "j2ee", "aaa@bbb.ccc", "en_US", Instant.now(),
				status, history, new BigDecimal("33.00"), null, null, null, List.of(line));
	}
}
