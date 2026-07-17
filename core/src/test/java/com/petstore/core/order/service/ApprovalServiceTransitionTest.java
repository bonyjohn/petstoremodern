package com.petstore.core.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;
import com.petstore.core.common.EventPublisher;
import com.petstore.core.common.OrderStatusChangedEvent;
import com.petstore.core.order.document.OrderDocument;
import com.petstore.core.order.document.OrderStatus;
import com.petstore.core.order.document.StatusChange;
import com.petstore.core.order.repository.OrderRepository;

/**
 * The approve/deny write is a status-preconditioned conditional update: when a
 * concurrent transition wins the race (modified count 0), the loser throws
 * instead of double-writing — and never publishes a duplicate event.
 */
class ApprovalServiceTransitionTest {

	private OrderRepository orderRepository;
	private EventPublisher eventPublisher;
	private MongoTemplate mongoTemplate;
	private ApprovalService approvalService;

	@BeforeEach
	void setUp() {
		orderRepository = mock(OrderRepository.class);
		eventPublisher = mock(EventPublisher.class);
		mongoTemplate = mock(MongoTemplate.class);
		approvalService = new ApprovalService(orderRepository, eventPublisher, mongoTemplate);
	}

	@Test
	void approvingAPendingOrderWritesAndPublishes() {
		givenOrder(OrderStatus.PENDING);
		givenUpdateModifies(1);

		approvalService.approve("1001");

		verify(eventPublisher).publish(new OrderStatusChangedEvent("1001", "APPROVED", "aaa@bbb.ccc"));
	}

	@Test
	void losingTheRaceToAConcurrentTransitionThrowsAndPublishesNothing() {
		givenOrder(OrderStatus.PENDING);
		givenUpdateModifies(0); // another writer flipped the status first

		assertThatThrownBy(() -> approvalService.approve("1001"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("concurrently");
		verify(eventPublisher, never()).publish(any());
	}

	@Test
	void approvingAnAlreadyApprovedOrderFailsTheStateMachineBeforeAnyWrite() {
		givenOrder(OrderStatus.APPROVED);

		assertThatThrownBy(() -> approvalService.approve("1001"))
				.isInstanceOf(IllegalStateException.class);
		verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(OrderDocument.class));
		verify(eventPublisher, never()).publish(any());
	}

	private void givenOrder(OrderStatus status) {
		OrderDocument order = new OrderDocument("1001", "j2ee", "aaa@bbb.ccc", "en_US", Instant.now(),
				status, List.of(new StatusChange(OrderStatus.PENDING, Instant.now())),
				new BigDecimal("16.50"), null, null, null, List.of());
		when(orderRepository.findById("1001")).thenReturn(Optional.of(order));
	}

	private void givenUpdateModifies(long modifiedCount) {
		when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(OrderDocument.class)))
				.thenReturn(UpdateResult.acknowledged(modifiedCount, modifiedCount, null));
	}
}
