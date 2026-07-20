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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sun.net.httpserver.HttpServer;

/**
 * End-to-end characterization of the shipping pipeline against a throwaway
 * replica-set MongoDB, with core's callback stubbed by a plain JDK HttpServer
 * that also applies the callback to the order document the way core's
 * ShipmentService would (advance qtyShipped, flip the status). Covers: stream
 * delivery + bounded decrement, and replay idempotency. Ordered: the replay
 * test builds on the first test's order and inventory state. The periodic
 * sweep is configured long (10m) so it never interferes.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
		"petstore.fulfillment.sweep-interval=10m",
		"petstore.fulfillment.lease-ttl=5s",
		"petstore.fulfillment.lease-renew-interval=500ms"})

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class FulfillmentPipelineIntegrationTest {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8").withReplicaSet();

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
		mongoTemplate.getCollection("orders").updateOne(
				Filters.eq("_id", "o-1"), Updates.set("status", "APPROVED"));
		mongoTemplate.getCollection("orders").insertOne(approvedOrder("o-2", "EST-1", 2));

		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(callbacks).containsKey("o-2"));
		assertThat(callbacks.get("o-1")).hasSize(1);
		assertThat(onHand("EST-1")).isEqualTo(9997L);
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
