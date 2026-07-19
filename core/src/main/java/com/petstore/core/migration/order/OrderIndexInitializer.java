package com.petstore.core.migration.order;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.petstore.core.order.document.OrderDocument;

/**
 * Creates the {@code orders} indexes during the seed run, like the catalog
 * seeder does for its collections: the one-time {@code petstore.seed=true} boot
 * is how every new environment is provisioned, data and indexes alike.
 * {@code customers} deliberately has none beyond {@code _id}: it is only ever
 * read by id.
 */
@Component
@ConditionalOnProperty(name = "petstore.seed", havingValue = "true")
public class OrderIndexInitializer implements ApplicationRunner {

	private final MongoTemplate mongoTemplate;

	public OrderIndexInitializer(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		mongoTemplate.indexOps(OrderDocument.class)
				.createIndex(new Index().on("userId", Direction.ASC).on("orderDate", Direction.DESC));
		mongoTemplate.indexOps(OrderDocument.class)
				.createIndex(new Index().on("status", Direction.ASC).on("orderDate", Direction.DESC));
	}
}
