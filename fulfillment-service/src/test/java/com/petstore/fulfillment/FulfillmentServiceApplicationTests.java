package com.petstore.fulfillment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots against a throwaway MongoDB like every other integration test — this
 * context starts a real change-stream consumer, which must never tail the
 * developer's live database (it could steal real work and clobber the real
 * service's checkpoint).
 */
@Testcontainers
@SpringBootTest
class FulfillmentServiceApplicationTests {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

	@Test
	void contextLoads() {
	}

}
