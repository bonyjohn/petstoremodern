package com.petstore.fulfillment.migration;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Seeds one inventory document per catalog item, gated on {@code petstore.seed=true}
 * like the core seeders. Item ids come straight from the {@code items} collection —
 * at seed time the collection is the contract between the services, not any shared
 * code. {@code $setOnInsert} keeps live stock counts intact on a re-seed; only
 * missing items get the legacy default of 10000 on hand.
 */
@Component
@ConditionalOnProperty(name = "petstore.seed", havingValue = "true")
public class InventorySeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(InventorySeeder.class);
	private static final long LEGACY_DEFAULT_QUANTITY = 10000;

	private final MongoTemplate mongoTemplate;

	public InventorySeeder(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<String> itemIds = new ArrayList<>();
		mongoTemplate.getCollection("items").distinct("_id", String.class).into(itemIds);

		// An empty bulk must not execute (the driver rejects it) — and an empty items
		// collection means core hasn't seeded yet, which is worth a loud hint.
		if (itemIds.isEmpty()) {
			log.warn("Inventory not seeded: items collection is empty (has core seeded yet?)");
			return;
		}

		BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "inventory");
		for (String itemId : itemIds) {
			bulkOps.upsert(
					Query.query(Criteria.where("_id").is(itemId)),
					new Update().setOnInsert("quantityOnHand", LEGACY_DEFAULT_QUANTITY));
		}
		bulkOps.execute();

		log.info("Inventory seeded: {} items at up to {} on hand each", itemIds.size(), LEGACY_DEFAULT_QUANTITY);
	}
}
