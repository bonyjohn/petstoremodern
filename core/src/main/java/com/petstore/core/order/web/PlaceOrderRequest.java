package com.petstore.core.order.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/orders}. Carries no prices and no identity — prices
 * are looked up server-side and the user comes from the JWT.
 */
public record PlaceOrderRequest(
		String locale,
		@NotEmpty @Valid List<OrderLineRequest> lines,
		@NotNull OrderContactDto shipTo,
		@NotNull OrderContactDto billTo,
		@NotNull OrderCreditCardDto creditCard) {
}
