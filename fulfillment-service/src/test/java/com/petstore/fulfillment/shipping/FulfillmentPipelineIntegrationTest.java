package com.petstore.fulfillment.shipping;

import static org.assertj.core.api.Assertions.assertThat;
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
 * End-to-end characterization of the change-stream shipping pipeline against a
 * throwaway replica-set MongoDB, with core's callback stubbed by a plain JDK
 * HttpServer: approval triggers a bounded inventory decrement and a callback;
 * replays don't double-decrement; and a stopped listener catches up from its
 * resume token on restart. Ordered: the scenarios share the running listener
 * and build on each other's state.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestMethodOrder(OrderAnnotation.class)
class FulfillmentPipelineIntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

	/** Stub core: records every shipment callback it receives, keyed by order id. */
	static final Map<String, List<String>> callbacks = new ConcurrentHashMap<>();
	static HttpServer stubCore;

	@DynamicPropertySource
	static void stubCoreUrl(DynamicPropertyRegistry registry) throws IOException {
		stubCore = HttpServer.create(new InetSocketAddress(0), 0);
		stubCore.createContext("/", exchange -> {
			// Path shape: /api/internal/orders/{id}/shipments
			String[] parts = exchange.getRequestURI().getPath().split("/");
			String orderId = parts[parts.length - 2];
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			callbacks.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()).add(body);
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		stubCore.start();
		registry.add("petstore.core.url", () -> "http://localhost:" + stubCore.getAddress().getPort());
	}

	@AfterAll
	static void stopStubCore() {
		stubCore.stop(0);
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private OrderChangeStreamListener listener;

	@Test
	@Order(1)
	void approvedOrderDecrementsInventoryAndCallsCoreBack() {
		mongoTemplate.getCollection("inventory")
				.insertOne(new Document("_id", "EST-1").append("quantityOnHand", 10000L));

		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-1", 1, 0));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(callbacks).containsKey("o-1");
			assertThat(onHand("EST-1")).isEqualTo(9999L);
		});
		assertThat(callbacks.get("o-1").get(0)).contains("\"itemId\":\"EST-1\"", "\"qtyShipped\":1");
	}

	@Test
	@Order(2)
	void replayedApprovalOfAShippedOrderDoesNotDecrementAgain() {
		// Simulate core having recorded the shipment, then a redelivery of the same
		// approved order: the update below re-fires the change stream with
		// qtyShipped already at qty, so there is nothing left to ship.
		mongoTemplate.getCollection("orders").updateOne(
				Filters.eq("_id", "o-1"), Updates.set("lines.0.qtyShipped", 1));

		// A second order acts as the fence: once it's processed, the replayed
		// o-1 event (which precedes it in the stream) has been consumed too.
		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-2", 2, 0));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(callbacks).containsKey("o-2"));
		assertThat(callbacks.get("o-1")).hasSize(1); // no second callback for the replay
		assertThat(onHand("EST-1")).isEqualTo(9997L); // 10000 - 1 (o-1) - 2 (o-2), nothing double-counted
	}

	@Test
	@Order(3)
	void restartResumesFromTheCheckpointAndCatchesUpOnMissedApprovals() {
		listener.stop();

		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-3", 3, 0));

		listener.start();

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			assertThat(callbacks).containsKey("o-3");
			assertThat(onHand("EST-1")).isEqualTo(9994L);
		});
	}

	private Document approvedOrder(String orderId, int qty, int qtyShipped) {
		return new Document("_id", orderId)
				.append("userId", "j2ee")
				.append("status", "APPROVED")
				.append("lines", List.of(new Document("lineNo", 1)
						.append("itemId", "EST-1")
						.append("qty", qty)
						.append("qtyShipped", qtyShipped)));
	}

	private long onHand(String itemId) {
		Document stock = mongoTemplate.findById(itemId, Document.class, "inventory");
		return ((Number) stock.get("quantityOnHand")).longValue();
	}
}
