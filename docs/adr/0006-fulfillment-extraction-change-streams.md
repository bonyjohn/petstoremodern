# 6. Fulfillment extraction: change streams, resume tokens, idempotent shipping

Date: 2026-07-15

## Status

Accepted

## Context

The legacy supplier is a separate EAR consuming order XML from JMS queues,
decrementing inventory, and sending shipment messages back. We want one real
service extraction to prove the module seams — with the same properties the
queues gave the legacy: async, restart-safe, no lost orders.

## Decision

**`fulfillment-service` shares no code with core** — only the MongoDB
collections (reads `orders`, owns `inventory`) and one HTTP callback contract.
It validates the same HS256 JWTs for its admin inventory API by duplicating
core's small JWT config: the services share a token format, not a library.

**Approved orders arrive via a MongoDB change stream** on `orders`
(insert/update/replace where `fullDocument.status == "APPROVED"`,
`updateLookup`), replacing the JMS queue. After each processed event the
**resume token is checkpointed** in `fulfillment_checkpoints`; on startup the
stream resumes from it, so orders approved while the service was down are
processed on restart (integration-tested by killing and restarting the
consumer). First-ever start begins at the current time.

**Delivery is at-least-once, made safe by two guards.** For each line the
service ships `min(qty − qtyShipped, quantityOnHand)`, decrementing inventory
with a conditional update (`quantityOnHand >= take`) so stock never goes
negative; core's callback applies `qtyShipped = min(qty, qtyShipped +
reported)` and skips no-change transitions. A replayed event therefore finds
`qty − qtyShipped = 0` and does nothing. **Honest caveat:** a crash *between*
the inventory decrement and the callback loses that decrement's report — the
replay re-ships from the order's (un-advanced) `qtyShipped`, so inventory can
be decremented twice for that window. Acceptable here (inventory is
advisory stock, not money); the production fix is recording the reservation
and the callback in one transaction or an outbox.

**The callback is service-to-service** (`POST /api/internal/orders/{id}/
shipments`) authenticated by a shared static header token
(`X-Internal-Token`) — dev-simple, explicitly a placeholder for mTLS or
OAuth2 client-credentials in production.

## Consequences

- Kill/restart and replay behavior are asserted by integration tests, not
  assumed — the properties the legacy got from JMS persistence are proven,
  not inherited.
- Collections-as-contract couples the services to the `orders` document
  schema; a schema registry / versioned events would replace this at scale.
- The change stream requires a replica set, so local dev Mongo runs as a
  single-node replica set (compose handles initiation).
- One more service to run locally — a second `spring-boot:run` or IDE launch;
  it tolerates starting late, catching up from its checkpoint.
