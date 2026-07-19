package com.petstore.core.order.service;

import static com.petstore.core.order.document.OrderStatus.APPROVED;
import static com.petstore.core.order.document.OrderStatus.COMPLETED;
import static com.petstore.core.order.document.OrderStatus.PARTIALLY_SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
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
import com.petstore.core.order.repository.OrderRepository;
import com.petstore.core.order.web.ShipmentLine;

/**
 * Core shipment behavior: a partial shipment moves the order to
 * PARTIALLY_SHIPPED, a full shipment completes it, an unknown order is a 404.
 */
class ShipmentServiceTest {

	private static final String ORDER_ID = "1001";

	private final OrderRepository orders = mock(OrderRepository.class);
	private final EventPublisher events = mock(EventPublisher.class);
	private final MongoTemplate mongo = mock(MongoTemplate.class);
	private final ShipmentService service = new ShipmentService(orders, events, mongo);

	@Test
	void partialShipmentMovesToPartiallyShippedAndPublishes() {
		givenOrder(order(APPROVED, 2, 0));
		givenUpdateModifies(1);

		ship(1);

		Update update = theUpdate();
		assertThat(shippedQty(update)).isEqualTo(1);
		assertThat(newStatus(update)).isEqualTo(PARTIALLY_SHIPPED);
		assertThat(update.getUpdateObject().containsKey("$push")).isTrue();
		verify(events).publish(new OrderStatusChangedEvent(ORDER_ID, "PARTIALLY_SHIPPED", "aaa@bbb.ccc"));
	}

	@Test
	void shippingEverythingCompletesTheOrder() {
		givenOrder(order(APPROVED, 2, 0));
		givenUpdateModifies(1);

		ship(2);

		assertThat(newStatus(theUpdate())).isEqualTo(COMPLETED);
		verify(events).publish(new OrderStatusChangedEvent(ORDER_ID, "COMPLETED", "aaa@bbb.ccc"));
	}

	@Test
	void unknownOrderIsNotFound() {
		when(orders.findById("nope")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.recordShipments("nope", List.of(new ShipmentLine("EST-1", 1))))
				.isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
	}

	private void ship(int qty) {
		service.recordShipments(ORDER_ID, List.of(new ShipmentLine("EST-1", qty)));
	}

	private void givenOrder(OrderDocument order) {
		when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
	}

	private void givenUpdateModifies(long n) {
		when(mongo.updateFirst(any(Query.class), any(Update.class), eq(OrderDocument.class)))
				.thenReturn(UpdateResult.acknowledged(n, n, null));
	}

	private Update theUpdate() {
		ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
		verify(mongo).updateFirst(any(Query.class), captor.capture(), eq(OrderDocument.class));
		return captor.getValue();
	}

	private Object newStatus(Update update) {
		return set(update).get("status");
	}

	@SuppressWarnings("unchecked")
	private int shippedQty(Update update) {
		return ((List<OrderLine>) set(update).get("lines")).get(0).qtyShipped();
	}

	private Document set(Update update) {
		return update.getUpdateObject().get("$set", Document.class);
	}

	private OrderDocument order(OrderStatus status, int qty, int qtyShipped) {
		OrderLine line = new OrderLine(1, "EST-1", "FI-SW-01", "FISH", qty, new BigDecimal("16.50"), qtyShipped);
		return new OrderDocument(ORDER_ID, "j2ee", "aaa@bbb.ccc", "en_US", Instant.now(), status, List.of(),
				new BigDecimal("33.00"), null, null, null, List.of(line));
	}
}
