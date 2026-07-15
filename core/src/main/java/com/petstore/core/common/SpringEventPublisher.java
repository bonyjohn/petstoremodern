package com.petstore.core.common;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** In-process {@link EventPublisher} backed by Spring's application event bus. */
@Component
public class SpringEventPublisher implements EventPublisher {

	private final ApplicationEventPublisher applicationEventPublisher;

	public SpringEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void publish(Object event) {
		applicationEventPublisher.publishEvent(event);
	}
}
