package com.petstore.core.order.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.petstore.core.order.document.OrderDocument;

public interface OrderRepository extends MongoRepository<OrderDocument, String> {

	List<OrderDocument> findByUserIdOrderByOrderDateDesc(String userId);
}
