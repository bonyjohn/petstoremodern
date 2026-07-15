package com.petstore.core.common;

/**
 * Publishes domain events. In-process (Spring application events) for now —
 * the legacy used JMS queues between EJBs; swapping this implementation for a
 * message broker later touches no domain code.
 */
public interface EventPublisher {

	void publish(Object event);
}
