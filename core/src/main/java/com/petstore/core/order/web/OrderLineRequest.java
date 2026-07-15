package com.petstore.core.order.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** One requested line at checkout: just the item and quantity — pricing is server-side. */
public record OrderLineRequest(@NotBlank String itemId, @Min(1) int qty) {
}
