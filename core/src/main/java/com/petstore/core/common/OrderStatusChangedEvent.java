package com.petstore.core.common;

/**
 * An order moved to a new status (e.g. approved or denied). Lives in common —
 * with the status as a plain string — so the notification module can listen
 * without depending on the order module's types.
 */
public record OrderStatusChangedEvent(String orderId, String status, String email) {
}
