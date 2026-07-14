package com.petstore.core.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.petstore.core.catalog.document.ProductDocument;

public interface ProductRepository extends MongoRepository<ProductDocument, String> {

	Page<ProductDocument> findByCategoryId(String categoryId, Pageable pageable);

	@Query("{ '$text': { '$search': ?0 } }")
	Page<ProductDocument> search(String text, Pageable pageable);
}
