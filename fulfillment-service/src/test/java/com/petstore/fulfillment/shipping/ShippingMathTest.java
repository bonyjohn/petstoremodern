package com.petstore.fulfillment.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShippingMathTest {

	@Test
	void shipsTheUnshippedRemainderWhenStockCoversIt() {
		assertThat(ShippingMath.quantityToShip(5, 0, 10000)).isEqualTo(5);
		assertThat(ShippingMath.quantityToShip(5, 2, 10000)).isEqualTo(3);
	}

	@Test
	void capsAtWhatIsOnHand() {
		assertThat(ShippingMath.quantityToShip(5, 0, 3)).isEqualTo(3);
		assertThat(ShippingMath.quantityToShip(5, 2, 1)).isEqualTo(1);
	}

	@Test
	void shipsNothingWhenFullyShippedOrOutOfStock() {
		assertThat(ShippingMath.quantityToShip(5, 5, 10000)).isZero();
		assertThat(ShippingMath.quantityToShip(5, 0, 0)).isZero();
	}

	@Test
	void neverGoesNegativeOnOverShippedReplays() {
		assertThat(ShippingMath.quantityToShip(5, 7, 10000)).isZero();
	}
}
