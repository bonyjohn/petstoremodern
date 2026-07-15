# 5. Order aggregate, counter ids, in-process events, legacy approval rule

Date: 2026-07-15

## Status

Accepted

## Context

The legacy order pipeline spans PurchaseOrder/LineItem/ContactInfo/Address/
CreditCard tables, a uidgen entity bean for order ids, and an XML-over-JMS
flow through the order processing center where `PurchaseOrderMDB.canIApprove`
auto-approves small orders and a human approves the rest. We need the same
capabilities without EJBs or JMS, in a shape that can later split into
services.

## Decision

**One `orders` document per order**: user/email/locale/date, status, lines
(`{lineNo, itemId, productId, categoryId, qty, unitPrice, qtyShipped}`),
totals, and snapshot ship-to/bill-to/card. The contact records are
**module-local types, deliberately duplicating the customer module's shape**
— an order is a point-in-time snapshot, and the module boundary (ArchUnit-
enforced) outweighs the duplication. A `statusHistory` list of
`{status, at}` gives an audit trail the legacy never had.

**Ids from a `counters` collection** (`{_id:"order", seq}`) via atomic
`findAndModify $inc` — the MongoDB idiom for the legacy uidgen bean; first id
1001.

**The status lifecycle is a pure-function state machine**
(`OrderTransitions`): PENDING→APPROVED/DENIED, APPROVED→PARTIALLY_SHIPPED/
COMPLETED, PARTIALLY_SHIPPED→COMPLETED, DENIED/COMPLETED terminal. Every
transition anywhere in the system goes through it; illegal transitions throw
and surface as 409s.

**Async is in-process events behind an `EventPublisher` interface** (Spring
application events today). Placing an order publishes `OrderPlacedEvent`; the
approval listener runs synchronously; status changes publish
`OrderStatusChangedEvent` (status as a plain string, in `common`, so the
notification module needs no order-module types). Swapping the implementation
for a broker touches no domain code.

**The auto-approval rule is verbatim legacy** (`canIApprove`): en_US strictly
< $500, ja_JP strictly < ¥50000, **any other locale never auto-approves** —
zh_CN orders always wait for the admin, exactly as in 1.3.1. Faithful
migration first; changing the business rule is a separate decision someone
can make later with this ADR in hand.

## Consequences

- Order reads are one document; the admin queue is one indexedable query.
- Sequential ids are a single-document hotspot at extreme write rates —
  irrelevant here, and swappable for ranged allocation if it ever matters.
- Boundary tests pin the odd-looking legacy behavior ($500.00 exactly is NOT
  auto-approved; ¥142 zh_CN is not either) so nobody "fixes" it by accident.
- In-process events mean approval shares the request transaction-ish window;
  the 201 response simply re-reads the order so callers see whatever the
  pipeline already did.
