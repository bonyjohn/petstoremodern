package com.petstore.fulfillment.shipping;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bson.BsonDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;

/**
 * Watches the {@code orders} collection (the modern stand-in for the legacy
 * XML-over-JMS order queue) for orders reaching APPROVED and hands each to the
 * {@link ApprovedOrderProcessor}. The resume token is checkpointed in
 * {@code fulfillment_checkpoints} after every processed event, so a restart
 * picks up exactly where it left off — orders approved while this service was
 * down are processed on the next start. A first-ever start (no checkpoint)
 * begins at the current point in time. Delivery is at-least-once: a crash
 * between processing and checkpointing replays the event, which is safe because
 * shipping is idempotent end to end (see {@link ApprovedOrderProcessor}).
 */
@Component
public class OrderChangeStreamListener implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(OrderChangeStreamListener.class);
	private static final String CHECKPOINT_COLLECTION = "fulfillment_checkpoints";
	private static final String CHECKPOINT_ID = "orders";

	private final MongoTemplate mongoTemplate;
	private final ApprovedOrderProcessor processor;

	private volatile boolean running;
	private Thread thread;

	public OrderChangeStreamListener(MongoTemplate mongoTemplate, ApprovedOrderProcessor processor) {
		this.mongoTemplate = mongoTemplate;
		this.processor = processor;
	}

	@Override
	public void start() {
		running = true;
		thread = new Thread(this::watchLoop, "order-change-stream");
		thread.setDaemon(true);
		thread.start();
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
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private void watchLoop() {
		while (running) {
			try (MongoCursor<ChangeStreamDocument<Document>> cursor = openCursor()) {
				while (running) {
					// tryNext returns null after maxAwaitTime, giving the loop a
					// chance to notice a stop request without a cross-thread close.
					ChangeStreamDocument<Document> event = cursor.tryNext();
					if (event == null) {
						continue;
					}
					processor.process(event.getFullDocument());
					saveCheckpoint(event.getResumeToken());
				}
			} catch (Exception e) {
				if (running) {
					log.warn("Change stream failed, reopening from last checkpoint", e);
					sleepQuietly();
				}
			}
		}
	}

	private MongoCursor<ChangeStreamDocument<Document>> openCursor() {
		List<org.bson.conversions.Bson> pipeline = List.of(Aggregates.match(Filters.and(
				Filters.in("operationType", List.of("insert", "update", "replace")),
				Filters.eq("fullDocument.status", "APPROVED"))));

		ChangeStreamIterable<Document> stream = mongoTemplate.getCollection("orders")
				.watch(pipeline)
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

	private void sleepQuietly() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
