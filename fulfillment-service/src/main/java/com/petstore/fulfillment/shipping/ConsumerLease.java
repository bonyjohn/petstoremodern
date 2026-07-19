package com.petstore.fulfillment.shipping;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * A TTL'd leader-election lease so exactly one fulfillment instance consumes the
 * order stream at a time — two independent consumers would each decrement
 * inventory for the same approved order. Acquire/steal is a single atomic
 * upsert with the current holder or an expired {@code expiresAt} as the
 * precondition: the same findAndModify-style primitive the core uses for its
 * order-id counters. The lease document shares the {@code fulfillment_checkpoints}
 * collection (infrastructure state, different {@code _id}). If the holder dies
 * without releasing, the lease is stealable once its TTL passes.
 */
@Component
public class ConsumerLease {

	private static final String COLLECTION = "fulfillment_checkpoints";
	private static final String LEASE_ID = "order-consumer-lease";

	private final MongoTemplate mongoTemplate;
	private final Duration ttl;
	private final Duration renewInterval;
	private final String instanceId = UUID.randomUUID().toString();

	private volatile boolean held;
	private volatile long lastRenewedMillis;

	public ConsumerLease(MongoTemplate mongoTemplate,
			@Value("${petstore.fulfillment.lease-ttl}") Duration ttl,
			@Value("${petstore.fulfillment.lease-renew-interval}") Duration renewInterval) {
		this.mongoTemplate = mongoTemplate;
		this.ttl = ttl;
		this.renewInterval = renewInterval;
	}


	public boolean acquireOrRenew() {
		long now = System.currentTimeMillis();
		if (held && now - lastRenewedMillis < renewInterval.toMillis()) {
			return true;
		}
		try {
			Query free = Query.query(Criteria.where("_id").is(LEASE_ID).orOperator(
					Criteria.where("holder").is(instanceId),
					Criteria.where("expiresAt").lt(new Date(now))));
			Update claim = new Update()
					.set("holder", instanceId)
					.set("expiresAt", new Date(now + ttl.toMillis()));
			mongoTemplate.upsert(free, claim, COLLECTION);
			held = true;
			lastRenewedMillis = now;
			return true;
		} catch (DuplicateKeyException e) {
			held = false;
			return false;
		} catch (DataAccessException e) {
			held = false;
			return false;
		}
	}

	public boolean held() {
		return held;
	}

	public void release() {
		if (!held) {
			return;
		}
		held = false;
		mongoTemplate.updateFirst(
				Query.query(Criteria.where("_id").is(LEASE_ID).and("holder").is(instanceId)),
				new Update().set("expiresAt", new Date(0)),
				COLLECTION);
	}
}
