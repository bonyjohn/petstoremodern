package com.petstore.core.order.service;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Issues sequential order ids from a {@code counters} collection document
 * ({@code {_id: "order", seq}}) with an atomic findAndModify {@code $inc} —
 * the MongoDB replacement for the legacy uidgen entity bean. The first id
 * issued is 1001 (seq starts at 1 via upsert; ids are offset by 1000).
 */
@Component
public class OrderIdGenerator {

	private final MongoTemplate mongoTemplate;

	public OrderIdGenerator(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public String nextOrderId() {
		Document counter = mongoTemplate.findAndModify(
				Query.query(Criteria.where("_id").is("order")),
				new Update().inc("seq", 1),
				FindAndModifyOptions.options().returnNew(true).upsert(true),
				Document.class, "counters");
		long seq = ((Number) counter.get("seq")).longValue();
		return String.valueOf(1000 + seq);
	}
}
