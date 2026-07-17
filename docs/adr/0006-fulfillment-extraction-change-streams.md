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
consumer).

**The change stream is a latency optimization; a reconciliation sweep is the
delivery guarantee.** The sweep queries `orders` for `status ∈ [APPROVED,
PARTIALLY_SHIPPED]` with any line's `qtyShipped < qty` and feeds each to the
same idempotent processor. It runs (a) before opening the stream whenever no
checkpoint exists — a first-ever start would otherwise begin at "now" and
permanently miss anything approved earlier; (b) when the checkpointed resume
token has aged out of the oplog (`ChangeStreamHistoryLost`, code 286): the
dead checkpoint is dropped, the sweep covers the gap, and the stream reopens
fresh — never an infinite retry on a dead token; and (c) periodically
(`petstore.fulfillment.sweep-interval`), which also restores the legacy
backorder behavior: an order stalled on empty stock ships within one interval
of restocking. With a checkpoint present the resumed stream replays missed
events itself and the acquire-time sweep is deliberately skipped — replayed
*insert* events carry insert-time state, so sweeping on top of a resume would
double-process them.

**Exactly one active consumer, enforced by a lease.** Two independent
consumers would each decrement inventory for the same approved order (the
qtyShipped guard protects the order document, not stock), so consumption is
deliberately single-instance: a TTL'd lease document (same
`fulfillment_checkpoints` collection, different `_id`) is acquired and renewed
with an atomic conditional upsert — the same findAndModify primitive as the
core's order-id counters. A standby instance polls, takes over when the
holder's lease expires or is released, and resumes from the shared checkpoint;
that lease is the HA story. Scaling *throughput* beyond one consumer means
partitioned streams or a real broker — the same evolution path already named
below.

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

- Kill/restart, replay, first-ever-start, restock-after-stall, and lease
  takeover are asserted by integration tests, not assumed — the properties the
  legacy got from JMS persistence are proven, not inherited.
- Collections-as-contract couples the services to the `orders` document
  schema; a schema registry / versioned events would replace this at scale.
- The change stream requires a replica set, so local dev Mongo runs as a
  single-node replica set (compose handles initiation).
- One more service to run locally — a second `spring-boot:run` or IDE launch;
  starting late is safe: with a checkpoint it catches up from the stream, and
  without one the pre-stream sweep ships whatever it missed.
- The sweep can re-ship an order whose callback is still in flight — the same
  bounded window as the decrement→callback crash above, capped per line by
  the remaining quantity; the outbox/transaction fix covers both.
