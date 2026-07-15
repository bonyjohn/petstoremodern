package com.petstore.core.order.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** Body of the fulfillment service's shipment callback. */
public record ShipmentRequest(@NotEmpty @Valid List<ShipmentLine> shipments) {
}
