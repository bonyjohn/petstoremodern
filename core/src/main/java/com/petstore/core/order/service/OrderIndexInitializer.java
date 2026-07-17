package com.petstore.core.order.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.petstore.core.order.document.OrderDocument;

/**
 * Ensures the {@code orders} indexes on every startup (not gated on seeding —
 * orders are written by the application, not the migration). {@code customers}
 * deliberately has none beyond {@code _id}: it is only ever read by id.
 */
@Component
public class OrderIndexInitializer implements ApplicationRunner {

	private final MongoTemplate mongoTemplate;

	public OrderIndexInitializer(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		// Serves GET /api/orders — a shopper's own orders, newest first.
		mongoTemplate.indexOps(OrderDocument.class)
				.createIndex(new Index().on("userId", Direction.ASC).on("orderDate", Direction.DESC));
		// Serves the admin queue's status filter, newest first.
		mongoTemplate.indexOps(OrderDocument.class)
				.createIndex(new Index().on("status", Direction.ASC).on("orderDate", Direction.DESC));
	}
}
