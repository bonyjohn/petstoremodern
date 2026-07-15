package com.petstore.core.order.service;

import static com.petstore.core.order.document.OrderStatus.APPROVED;
import static com.petstore.core.order.document.OrderStatus.COMPLETED;
import static com.petstore.core.order.document.OrderStatus.DENIED;
import static com.petstore.core.order.document.OrderStatus.PARTIALLY_SHIPPED;
import static com.petstore.core.order.document.OrderStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.petstore.core.order.document.OrderStatus;

/** Exhaustively characterizes the order state machine over every (current, target) pair. */
class OrderTransitionsTest {

	private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
			PENDING, Set.of(APPROVED, DENIED),
			APPROVED, Set.of(PARTIALLY_SHIPPED, COMPLETED),
			PARTIALLY_SHIPPED, Set.of(COMPLETED),
			DENIED, Set.of(),
			COMPLETED, Set.of());

	@Test
	void everyPairMatchesTheTransitionTable() {
		for (OrderStatus current : OrderStatus.values()) {
			for (OrderStatus target : OrderStatus.values()) {
				assertThat(OrderTransitions.isAllowed(current, target))
						.as("%s -> %s", current, target)
						.isEqualTo(ALLOWED.get(current).contains(target));
			}
		}
	}

	@Test
	void nextReturnsTheTargetForAnAllowedTransition() {
		assertThat(OrderTransitions.next(PENDING, APPROVED)).isEqualTo(APPROVED);
		assertThat(OrderTransitions.next(PENDING, DENIED)).isEqualTo(DENIED);
		assertThat(OrderTransitions.next(APPROVED, PARTIALLY_SHIPPED)).isEqualTo(PARTIALLY_SHIPPED);
		assertThat(OrderTransitions.next(APPROVED, COMPLETED)).isEqualTo(COMPLETED);
		assertThat(OrderTransitions.next(PARTIALLY_SHIPPED, COMPLETED)).isEqualTo(COMPLETED);
	}

	@Test
	void nextThrowsForAnIllegalTransition() {
		assertThatThrownBy(() -> OrderTransitions.next(DENIED, APPROVED))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DENIED")
				.hasMessageContaining("APPROVED");
		assertThatThrownBy(() -> OrderTransitions.next(COMPLETED, PENDING))
				.isInstanceOf(IllegalStateException.class);
	}
}
