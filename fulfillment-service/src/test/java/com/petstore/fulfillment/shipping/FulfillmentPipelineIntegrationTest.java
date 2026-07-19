package com.petstore.fulfillment.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sun.net.httpserver.HttpServer;

/**
 * End-to-end characterization of the shipping pipeline against a throwaway
 * replica-set MongoDB, with core's callback stubbed by a plain JDK HttpServer
 * that also applies the callback to the order document the way core's
 * ShipmentService would (advance qtyShipped, flip the status) — without that,
 * shipped orders would look eternally unshipped to the reconciliation sweep.
 * Covers: stream delivery + bounded decrement; replay idempotency; resume-token
 * catch-up; the acquire-time sweep (first-ever start); the sweep as backorder
 * restocking; and the consumer lease. Ordered: scenarios build on each other's
 * inventory state. The periodic sweep is configured long (10m) so tests drive
 * sweeps explicitly and deterministically.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
		"petstore.fulfillment.sweep-interval=10m",
		"petstore.fulfillment.lease-ttl=5s",
		"petstore.fulfillment.lease-renew-interval=500ms"})
@TestMethodOrder(OrderAnnotation.class)
class FulfillmentPipelineIntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

	/** Stub core: records every shipment callback and applies it to the order document. */
	static final Map<String, List<String>> callbacks = new ConcurrentHashMap<>();
	static HttpServer stubCore;
	static volatile MongoTemplate stubTemplate;

	@DynamicPropertySource
	static void stubCoreUrl(DynamicPropertyRegistry registry) throws IOException {
		stubCore = HttpServer.create(new InetSocketAddress(0), 0);
		stubCore.createContext("/", exchange -> {
			// Path shape: /api/internal/orders/{id}/shipments
			String[] parts = exchange.getRequestURI().getPath().split("/");
			String orderId = parts[parts.length - 2];
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			callbacks.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()).add(body);
			applyCallbackToOrder(orderId, body);
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		stubCore.start();
		registry.add("petstore.core.url", () -> "http://localhost:" + stubCore.getAddress().getPort());
	}

	/** What core's ShipmentService would do: advance qtyShipped (capped) and flip the status. */
	static void applyCallbackToOrder(String orderId, String body) {
		Document order = stubTemplate.getCollection("orders").find(Filters.eq("_id", orderId)).first();
		List<Document> lines = order.getList("lines", Document.class);
		for (Document shipment : Document.parse(body).getList("shipments", Document.class)) {
			for (Document line : lines) {
				if (line.getString("itemId").equals(shipment.getString("itemId"))) {
					int qty = line.getInteger("qty", 0);
					line.put("qtyShipped", Math.min(qty,
							line.getInteger("qtyShipped", 0) + shipment.getInteger("qtyShipped", 0)));
				}
			}
		}
		boolean allShipped = lines.stream()
				.allMatch(line -> line.getInteger("qtyShipped", 0) >= line.getInteger("qty", 0));
		stubTemplate.getCollection("orders").updateOne(Filters.eq("_id", orderId),
				Updates.combine(Updates.set("lines", lines),
						Updates.set("status", allShipped ? "COMPLETED" : "PARTIALLY_SHIPPED")));
	}

	@AfterAll
	static void stopStubCore() {
		stubCore.stop(0);
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private OrderChangeStreamListener listener;

	@Autowired
	private ApprovedOrderProcessor processor;

	@Autowired
	private ReconciliationSweep sweep;

	@BeforeEach
	void exposeTemplateToStub() {
		stubTemplate = mongoTemplate;
	}

	@Test
	@Order(1)
	void approvedOrderDecrementsInventoryAndCallsCoreBack() {
		mongoTemplate.getCollection("inventory")
				.insertOne(new Document("_id", "EST-1").append("quantityOnHand", 10000L));

		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-1", "EST-1", 1));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(callbacks).containsKey("o-1");
			assertThat(onHand("EST-1")).isEqualTo(9999L);
			assertThat(orderStatus("o-1")).isEqualTo("COMPLETED");
		});
		assertThat(callbacks.get("o-1").get(0)).contains("\"itemId\":\"EST-1\"", "\"qtyShipped\":1");
	}

	@Test
	@Order(2)
	void replayedApprovalOfAShippedOrderDoesNotDecrementAgain() {
		// Re-deliver o-1 as APPROVED with its qtyShipped already recorded: the
		// stream fires again, but there is nothing left to ship.
		mongoTemplate.getCollection("orders").updateOne(
				Filters.eq("_id", "o-1"), Updates.set("status", "APPROVED"));

		// A second order acts as the fence: once it's processed, the replayed
		// o-1 event (which precedes it in the stream) has been consumed too.
		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-2", "EST-1", 2));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(callbacks).containsKey("o-2"));
		assertThat(callbacks.get("o-1")).hasSize(1); // no second callback for the replay
		assertThat(onHand("EST-1")).isEqualTo(9997L); // 10000 - 1 (o-1) - 2 (o-2), nothing double-counted
	}

	@Test
	@Order(3)
	void restartResumesFromTheCheckpointAndCatchesUpOnMissedApprovals() {
		listener.stop();

		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-3", "EST-1", 3));

		listener.start();

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(callbacks).containsKey("o-3");
			assertThat(onHand("EST-1")).isEqualTo(9994L);
		});
	}

	@Test
	@Order(4)
	void startupSweepShipsOrdersApprovedBeforeTheFirstEverStart() {
		// No checkpoint = the stream alone would start at "now" and never see
		// this order; the acquire-time sweep is what ships it.
		listener.stop();
		mongoTemplate.getCollection("fulfillment_checkpoints").deleteOne(Filters.eq("_id", "orders"));

		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-4", "EST-1", 4));

		listener.start();

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(callbacks).containsKey("o-4");
			assertThat(onHand("EST-1")).isEqualTo(9990L);
		});
	}

	@Test
	@Order(5)
	void outOfStockOrderStallsUntilRestockingThenTheSweepShipsIt() {
		mongoTemplate.getCollection("inventory")
				.insertOne(new Document("_id", "EST-2").append("quantityOnHand", 0L));
		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-5", "EST-2", 2));

		// Fence: once o-6 is processed, o-5's stream event has been consumed too.
		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-6", "EST-1", 1));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(callbacks).containsKey("o-6"));
		assertThat(callbacks).doesNotContainKey("o-5"); // stalled: nothing to ship
		assertThat(orderStatus("o-5")).isEqualTo("APPROVED");

		// Restock, then reconcile — the legacy backorder behavior.
		mongoTemplate.getCollection("inventory").updateOne(
				Filters.eq("_id", "EST-2"), Updates.set("quantityOnHand", 10L));
		sweep.sweep();

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(callbacks).containsKey("o-5");
			assertThat(orderStatus("o-5")).isEqualTo("COMPLETED");
			assertThat(onHand("EST-2")).isEqualTo(8L);
		});
	}

	@Test
	@Order(6)
	void secondInstanceCannotAcquireTheLeaseWhileHeldAndTakesOverAfterRelease() {
		ConsumerLease leaseB = new ConsumerLease(mongoTemplate, Duration.ofSeconds(5), Duration.ofMillis(500));

		// The application's listener holds (and keeps renewing) the lease.
		assertThat(leaseB.acquireOrRenew()).isFalse();

		// Holder shuts down -> lease released -> the standby can take over.
		listener.stop();
		assertThat(leaseB.acquireOrRenew()).isTrue();

		// The takeover instance consumes from the shared checkpoint: no loss.
		OrderChangeStreamListener listenerB =
				new OrderChangeStreamListener(mongoTemplate, processor, leaseB, sweep);
		listenerB.start();
		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-7", "EST-1", 1));
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(callbacks).containsKey("o-7");
			assertThat(onHand("EST-1")).isEqualTo(9988L); // 9990 - 1 (o-6) - 1 (o-7)
		});
		listenerB.stop();
	}

	@Test
	@Order(7)
	void callbackFailureRestocksSoRetriesDoNotLeakInventory() {
		mongoTemplate.getCollection("inventory")
				.insertOne(new Document("_id", "EST-3").append("quantityOnHand", 50L));

		// A processor whose "core" is unreachable — the callback always fails.
		ApprovedOrderProcessor unreachableCore =
				new ApprovedOrderProcessor(mongoTemplate, "http://localhost:1", "irrelevant");
		ApprovedOrder order = ApprovedOrder.from(approvedOrder("o-8", "EST-3", 5));

		// Each attempt decrements, fails the callback, and compensates: however
		// many times the sweep retries, no stock leaks.
		for (int attempt = 0; attempt < 3; attempt++) {
			assertThatThrownBy(() -> unreachableCore.process(order)).isInstanceOf(RuntimeException.class);
		}
		assertThat(onHand("EST-3")).isEqualTo(50L);
	}

	private Document approvedOrder(String orderId, String itemId, int qty) {
		return new Document("_id", orderId)
				.append("userId", "j2ee")
				.append("status", "APPROVED")
				.append("lines", List.of(new Document("lineNo", 1)
						.append("itemId", itemId)
						.append("qty", qty)
						.append("qtyShipped", 0)));
	}

	private String orderStatus(String orderId) {
		return mongoTemplate.getCollection("orders").find(Filters.eq("_id", orderId)).first().getString("status");
	}

	private long onHand(String itemId) {
		Document stock = mongoTemplate.findById(itemId, Document.class, "inventory");
		return ((Number) stock.get("quantityOnHand")).longValue();
	}
}
