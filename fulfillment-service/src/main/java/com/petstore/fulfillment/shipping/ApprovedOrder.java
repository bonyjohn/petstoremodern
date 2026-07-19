package com.petstore.fulfillment.shipping;

import java.util.List;

import org.bson.Document;

/**
 * Fulfillment's own view of an approved order.
 */
public record ApprovedOrder(String orderId, List<Line> lines) {

	public record Line(String itemId, int qty, int qtyShipped) {
	}

	public static ApprovedOrder from(Document doc) {
		List<Line> lines = doc.getList("lines", Document.class).stream()
				.map(line -> new Line(
						line.getString("itemId"),
						line.getInteger("qty", 0),
						line.getInteger("qtyShipped", 0)))
				.toList();
		return new ApprovedOrder(doc.getString("_id"), lines);
	}
}
