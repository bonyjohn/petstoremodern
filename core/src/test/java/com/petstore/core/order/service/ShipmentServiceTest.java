package com.petstore.core.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;

import com.mongodb.client.result.UpdateResult;
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
 * qty, replays are silent no-ops, status follows the shipped quantities through
 * the state machine, and the write is a status-preconditioned conditional
 * update that re-reads once on a lost race.
 */
class ShipmentServiceTest {

	private OrderRepository orderRepository;
	private EventPublisher eventPublisher;
	private MongoTemplate mongoTemplate;
	private ShipmentService shipmentService;

	@BeforeEach
	void setUp() {
		orderRepository = mock(OrderRepository.class);
		eventPublisher = mock(EventPublisher.class);
		mongoTemplate = mock(MongoTemplate.class);
		shipmentService = new ShipmentService(orderRepository, eventPublisher, mongoTemplate);
	}

	@Test
	void partialShipmentMovesToPartiallyShippedAndPublishes() {
		givenOrder(approvedOrder(2, 0));
		givenUpdateModifies(1);

		shipmentService.recordShipments("1001", List.of(new ShipmentLine("EST-1", 1)));

		Update update = capturedUpdate();
		assertThat(shippedLines(update).get(0).qtyShipped()).isEqualTo(1);
		assertThat(setValue(update, "status")).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);
		assertThat(update.getUpdateObject().containsKey("$push")).isTrue(); // statusHistory audit entry
		verify(eventPublisher).publish(new OrderStatusChangedEvent("1001", "PARTIALLY_SHIPPED", "aaa@bbb.ccc"));
	}

	@Test
	void shippingEverythingCompletesTheOrder() {
		givenOrder(approvedOrder(2, 0));
		givenUpdateModifies(1);

		shipmentService.recordShipments("1001", List.of(new ShipmentLine("EST-1", 2)));

		Update update = capturedUpdate();
		assertThat(shippedLines(update).get(0).qtyShipped()).isEqualTo(2);
		assertThat(setValue(update, "status")).isEqualTo(OrderStatus.COMPLETED);
		verify(eventPublisher).publish(new OrderStatusChangedEvent("1001", "COMPLETED", "aaa@bbb.ccc"));
	}

	@Test
	void replayOnAFullyShippedLineIsANoOpAndPublishesNothing() {
		givenOrder(completedOrder(2, 2));

		shipmentService.recordShipments("1001", List.of(new ShipmentLine("EST-1", 2)));

		verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(OrderDocument.class));
		verify(eventPublisher, never()).publish(any());
	}

	@Test
	void overReportingClampsAtTheOrderedQuantity() {
		givenOrder(approvedOrder(2, 0));
		givenUpdateModifies(1);

		shipmentService.recordShipments("1001", List.of(new ShipmentLine("EST-1", 5)));

		Update update = capturedUpdate();
		assertThat(shippedLines(update).get(0).qtyShipped()).isEqualTo(2); // min(qty, 0 + 5)
		assertThat(setValue(update, "status")).isEqualTo(OrderStatus.COMPLETED);
	}

	@Test
	void lostRaceReReadsOnceAndNoOpsWhenTheOtherWriterAlreadyShippedIt() {
		// First read sees APPROVED; the conditional update loses to a concurrent
		// transition; the re-read finds the order fully shipped and completed.
		when(orderRepository.findById("1001"))
				.thenReturn(Optional.of(approvedOrder(2, 0)))
				.thenReturn(Optional.of(completedOrder(2, 2)));
		givenUpdateModifies(0);

		shipmentService.recordShipments("1001", List.of(new ShipmentLine("EST-1", 2)));

		verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(OrderDocument.class));
		verify(eventPublisher, never()).publish(any());
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

	private void givenUpdateModifies(long modifiedCount) {
		when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(OrderDocument.class)))
				.thenReturn(UpdateResult.acknowledged(modifiedCount, modifiedCount, null));
	}

	private Update capturedUpdate() {
		ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
		verify(mongoTemplate).updateFirst(any(Query.class), captor.capture(), eq(OrderDocument.class));
		return captor.getValue();
	}

	@SuppressWarnings("unchecked")
	private List<OrderLine> shippedLines(Update update) {
		return (List<OrderLine>) setValue(update, "lines");
	}

	private Object setValue(Update update, String field) {
		return update.getUpdateObject().get("$set", Document.class).get(field);
	}

	private OrderDocument approvedOrder(int qty, int qtyShipped) {
		return order(OrderStatus.APPROVED, qty, qtyShipped,
				List.of(new StatusChange(OrderStatus.PENDING, Instant.now()),
						new StatusChange(OrderStatus.APPROVED, Instant.now())));
	}

	private OrderDocument completedOrder(int qty, int qtyShipped) {
		return order(OrderStatus.COMPLETED, qty, qtyShipped,
				List.of(new StatusChange(OrderStatus.PENDING, Instant.now()),
						new StatusChange(OrderStatus.APPROVED, Instant.now()),
						new StatusChange(OrderStatus.COMPLETED, Instant.now())));
	}

	private OrderDocument order(OrderStatus status, int qty, int qtyShipped, List<StatusChange> history) {
		OrderLine line = new OrderLine(1, "EST-1", "FI-SW-01", "FISH", qty, new BigDecimal("16.50"), qtyShipped);
		return new OrderDocument("1001", "j2ee", "aaa@bbb.ccc", "en_US", Instant.now(),
				status, history, new BigDecimal("33.00"), null, null, null, List.of(line));
	}
}
