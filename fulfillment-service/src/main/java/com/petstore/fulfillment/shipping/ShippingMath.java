package com.petstore.fulfillment.shipping;

/**
 * The shipping quantity decision as a pure function, kept separate so it's
 * trivially testable.
 */
public final class ShippingMath {

	private ShippingMath() {
	}

	public static int quantityToShip(int qty, int qtyShipped, long quantityOnHand) {
		long remaining = qty - qtyShipped;
		return (int) Math.max(0, Math.min(remaining, quantityOnHand));
	}
}
