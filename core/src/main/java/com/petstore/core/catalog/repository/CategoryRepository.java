package com.petstore.core.catalog.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.petstore.core.catalog.document.CategoryDocument;

public interface CategoryRepository extends MongoRepository<CategoryDocument, String> {
}
