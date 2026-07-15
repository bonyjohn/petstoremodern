package com.petstore.fulfillment.inventory;

import jakarta.validation.constraints.Min;

/** Body of {@code PUT /api/inventory/{id}}: the new absolute on-hand quantity. */
public record InventoryUpdateRequest(@Min(0) long quantityOnHand) {
}
