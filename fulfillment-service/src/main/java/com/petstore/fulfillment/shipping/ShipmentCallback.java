package com.petstore.fulfillment.shipping;

import java.util.List;

/**
 * Body of the shipment callback to core's internal API — mirrors the wire shape
 * core's ShipmentRequest expects, the way any REST client mirrors a server's
 * contract.
 */
public record ShipmentCallback(List<ShipmentLine> shipments) {

	public record ShipmentLine(String itemId, int qtyShipped) {
	}
}
