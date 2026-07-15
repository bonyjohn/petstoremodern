package com.petstore.fulfillment.inventory;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * On-hand stock for one sellable item — {@code _id} is the item id (e.g. {@code EST-1}).
 * Replaces the legacy supplier app's Inventory entity bean/table.
 */
@Document("inventory")
public record InventoryDocument(@Id String id, long quantityOnHand) {
}
