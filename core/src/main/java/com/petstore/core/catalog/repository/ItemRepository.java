package com.petstore.core.catalog.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.petstore.core.catalog.document.ItemDocument;

public interface ItemRepository extends MongoRepository<ItemDocument, String> {
}
