package com.petstore.core.order.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** One shipped quantity reported by the fulfillment service. */
public record ShipmentLine(@NotBlank String itemId, @Min(1) int qtyShipped) {
}
