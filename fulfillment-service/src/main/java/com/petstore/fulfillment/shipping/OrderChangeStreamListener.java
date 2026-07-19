package com.petstore.fulfillment.shipping;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bson.BsonDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.MongoException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;

/**
 * Watches the {@code orders} collection (the modern stand-in for the legacy
 * XML-over-JMS order queue) for orders reaching APPROVED and hands each to the
 * {@link ApprovedOrderProcessor}. The stream is the low-latency path; the
 * {@link ReconciliationSweep} is the delivery guarantee behind it:
 * <ul>
 * <li>Only the instance holding the {@link ConsumerLease} consumes — a second
 * instance polls for the lease and takes over if the holder dies.</li>
 * <li>On acquiring the lease (including first-ever start, when no checkpoint
 * exists and the stream would otherwise begin at "now") a sweep ships anything
 * approved earlier.</li>
 * <li>The resume token is checkpointed in {@code fulfillment_checkpoints}
 * after every processed event; a restart resumes from it.</li>
 * <li>If the token has aged out of the oplog ({@code ChangeStreamHistoryLost},
 * error code 286), the dead checkpoint is dropped, a sweep covers the gap, and
 * the stream reopens fresh — no infinite retry on a dead token.</li>
 * </ul>
 * Delivery is at-least-once: a crash between processing and checkpointing
 * replays the event, which is safe because shipping is idempotent end to end
 * (see {@link ApprovedOrderProcessor}).
 */
@Component
public class OrderChangeStreamListener implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(OrderChangeStreamListener.class);
	private static final String CHECKPOINT_COLLECTION = "fulfillment_checkpoints";
	private static final String CHECKPOINT_ID = "orders";
	private static final int CHANGE_STREAM_HISTORY_LOST = 286;

	/** The subscription: writes that leave an order APPROVED. Static, so plain JSON — paste into mongosh to debug. */
	private static final Document APPROVED_ORDERS_MATCH = Document.parse("""
			{
			  "$match": {
			    "operationType": { "$in": ["insert", "update", "replace"] },
			    "fullDocument.status": "APPROVED"
			  }
			}
			""");

	private final MongoTemplate mongoTemplate;
	private final ApprovedOrderProcessor processor;
	private final ConsumerLease lease;
	private final ReconciliationSweep sweep;

	private volatile boolean running;
	private Thread thread;

	public OrderChangeStreamListener(MongoTemplate mongoTemplate, ApprovedOrderProcessor processor,
			ConsumerLease lease, ReconciliationSweep sweep) {
		this.mongoTemplate = mongoTemplate;
		this.processor = processor;
		this.lease = lease;
		this.sweep = sweep;
	}

	@Override
	public void start() {
		running = true;
		thread = new Thread(this::watchLoop, "order-change-stream");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public boolean isAutoStartup() {
		return false;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		if (!running) {
			start();
		}
	}

	@Override
	public void stop() {
		running = false;
		if (thread != null) {
			try {
				thread.join(TimeUnit.SECONDS.toMillis(10));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			thread = null;
		}
		lease.release();
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private void watchLoop() {
		while (running) {
			if (!lease.acquireOrRenew()) {
				sleepQuietly();
				continue;
			}
			try {
				if (loadCheckpoint() == null) {
					sweep.sweep();
				}
			} catch (Exception e) {
				log.warn("Pre-stream sweep failed; the periodic sweep will retry", e);
			}
			try (MongoCursor<ChangeStreamDocument<Document>> cursor = openCursor()) {
				while (running) {
					if (!lease.acquireOrRenew()) {
						break;
					}
					ChangeStreamDocument<Document> event = cursor.tryNext();
					if (event == null) {
						continue;
					}
					processor.process(ApprovedOrder.from(event.getFullDocument()));
					saveCheckpoint(event.getResumeToken());
				}
			} catch (Exception e) {
				if (!running) {
					break;
				}
				if (isHistoryLost(e)) {
					log.warn("Resume token aged out of the oplog; dropping checkpoint and reconciling by sweep");
					deleteCheckpoint();
					sweep.sweep();
				} else {
					log.warn("Change stream failed, reopening from last checkpoint", e);
					sleepQuietly();
				}
			}
		}
	}

	private boolean isHistoryLost(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof MongoException mongoException
					&& mongoException.getCode() == CHANGE_STREAM_HISTORY_LOST) {
				return true;
			}
		}
		return false;
	}

	private MongoCursor<ChangeStreamDocument<Document>> openCursor() {
		ChangeStreamIterable<Document> stream = mongoTemplate.getCollection("orders")
				.watch(List.of(APPROVED_ORDERS_MATCH))
				.fullDocument(FullDocument.UPDATE_LOOKUP)
				.maxAwaitTime(500, TimeUnit.MILLISECONDS);

		BsonDocument checkpoint = loadCheckpoint();
		if (checkpoint != null) {
			stream = stream.resumeAfter(checkpoint);
		}
		return stream.cursor();
	}

	private BsonDocument loadCheckpoint() {
		Document checkpoint = mongoTemplate.getCollection(CHECKPOINT_COLLECTION)
				.find(Filters.eq("_id", CHECKPOINT_ID)).first();
		if (checkpoint == null) {
			return null;
		}
		return BsonDocument.parse(checkpoint.get("token", Document.class).toJson());
	}

	private void saveCheckpoint(BsonDocument resumeToken) {
		mongoTemplate.getCollection(CHECKPOINT_COLLECTION).updateOne(
				Filters.eq("_id", CHECKPOINT_ID),
				Updates.set("token", Document.parse(resumeToken.toJson())),
				new com.mongodb.client.model.UpdateOptions().upsert(true));
	}

	private void deleteCheckpoint() {
		mongoTemplate.getCollection(CHECKPOINT_COLLECTION).deleteOne(Filters.eq("_id", CHECKPOINT_ID));
	}

	private void sleepQuietly() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
