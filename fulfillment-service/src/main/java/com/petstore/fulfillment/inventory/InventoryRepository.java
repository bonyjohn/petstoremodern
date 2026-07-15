package com.petstore.fulfillment.inventory;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventoryRepository extends MongoRepository<InventoryDocument, String> {
}
