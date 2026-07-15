# Migration Plan — Java Pet Store 1.3.1 → Spring Boot / MongoDB / Angular

How the problem was broken up, what order the pieces shipped in, and what the
same approach looks like at real scale. The decision log lives in [adr/](adr).

## 1. What we were migrating

Java Pet Store 1.3.1 is not a monolith — it is a **2002 distributed system**:
four EARs (storefront, admin, order processing center, supplier) integrating
through **seven JMS queues and eight message-driven beans**, passing XML
documents validated by **five DTDs**. Orders flow storefront → OPC → supplier →
back, as XML-over-JMS. State lives in container-managed entity beans over an
RDBMS, one aggregate shredded across many tables (a customer spans six).

Two consequences drove the plan:

- **The topology is not the requirement.** Queues-and-MDBs was how 2002 did
  async; the *capability* is "order approval and fulfillment happen
  asynchronously and survive restarts". We re-platform capabilities, not
  topology.
- **The behavior is the requirement.** Nobody has a spec; the running legacy
  app *is* the spec. So every slice starts from its legacy flow
  (DEMO_FLOW.md) and its real seed data (`Populate-UTF8.xml`), captured as
  characterization tests before/while the replacement is built.

## 2. Strategy

1. **Vertical slices, each deployable.** Every part ends with a runnable,
   demoable system — never a horizontal "all entities first" layer.
2. **Characterization tests first.** Integration tests assert legacy facts
   (names, prices, thresholds) against the real seed data on a real MongoDB
   (Testcontainers), so fidelity regressions are caught mechanically.
3. **Modular monolith with enforced seams, then extract once.** Business
   modules in one `core` service, boundaries enforced by ArchUnit from part 1.
   One real service extraction (fulfillment) proves the seams are real.
4. **Migration code is disposable scaffolding.** Everything that understands
   legacy formats lives in `migration/`, one-way dependency ArchUnit-enforced;
   retiring the legacy format is a package deletion.

## 3. Slice order (as shipped)

| Part | Slice | What it proved |
|---|---|---|
| 1 | Skeleton: two Boot services, Angular app, compose, CI, ArchUnit module rules | Walking skeleton; boundaries enforced before any code existed to violate them |
| 2 | Catalog: document model, legacy XML seeder, browse/search API + UI | Data-fidelity approach works (per-locale prices verbatim; drops counted, not silent) |
| 3 | Customer: 6-entity aggregate → one document, plaintext → BCrypt on migration, JWT auth, client cart | Aggregate collapse + auth upgrade in one auditable step |
| 4 | Orders: checkout with server-side re-pricing, sequential ids, in-process events, verbatim legacy approval rule, state machine | Async semantics begin as in-process events behind an interface |
| 5 | Admin + fulfillment extraction: change stream + resume tokens, inventory, guarded shipment callback, admin UI | The seam holds: a real second service sharing only collections + a callback contract |
| 6 | Hardening: coverage sweep, docs/ADRs, README quickstart verified literally | The system is explainable and reproducible by a stranger |

**The process self-corrects — two review-driven collapses.** In part 2, review
collapsed a planned `LegacyCatalog` + `CatalogConverter` staged pipeline into a
single parser once the document model became "verbatim per legacy level" and
the converter was revealed to be a field-by-field copy. In part 3 the customer
pipeline was built staged (parse → `Legacy*` records → convert) and review
collapsed it the same way: a plain id→password map looked up while streaming
customers replaced three classes with zero behavior change (the tests moved,
none were weakened). Intermediate models must earn their keep with a real
transformation; when they don't, they go.

## 4. Risk register

| Risk | Mitigation |
|---|---|
| **Environment resurrection** — the legacy app barely runs anywhere modern | Treat seed data + documented flows as the spec; never depend on executing the legacy system to make progress |
| **Data fidelity** — e.g. three *independent* per-locale price lists per item (¥1951 is not converted $16.50) | Migrate verbatim, never canonicalize; characterization tests pin exact values; seeders count and log every dropped record/locale |
| **Auth upgrade** — legacy stores plaintext passwords | Hash at migration time (BCrypt); the migration parser is the only code that ever reads the plaintext; tests assert hash ≠ plaintext and that legacy credentials still log in |
| **Async semantics** — order pipeline must stay async and restart-safe without JMS | In-process events behind `EventPublisher` (broker-swappable), then a change stream with resume-token checkpoints; at-least-once made safe by qtyShipped-guarded callbacks and bounded inventory decrements; kill/restart and replay are integration-tested |
| **Boundary erosion** — modules quietly coupling | ArchUnit fails the build on any cross-module or application→migration dependency |

## 5. At 100× scale

The shape of the plan survives; the constants change:

- **Same slicing.** Capabilities → vertical slices → strangler-style
  replacement per flow, highest-risk data first. More teams just means more
  slices in flight, each behind its own seam.
- **Seams first, extraction on demand.** The modular monolith with
  ArchUnit-enforced boundaries is the default; the fulfillment extraction is
  the playbook run once — collections-as-contract, token-secured callback,
  checkpointed consumer — repeatable mechanically for the next module that
  needs independent scaling or ownership.
- **`EventPublisher` → broker.** The single in-process interface swaps to
  Kafka/JMS without touching domain code; the change-stream consumer already
  practices the hard parts (resume tokens, at-least-once, idempotency).
- **Data migration at volume** becomes a batched/backfilled pipeline with the
  same shape: parse → count → upsert idempotently, with drop counters as the
  reconciliation report.

## 6. Deliberate cuts

| Cut | Rationale | Production path |
|---|---|---|
| Emails → log-only `Notifier` | The capability is "status changes notify the customer"; SMTP adds setup, zero migration insight | Swap the listener body for a mail/notification provider; the event contract already exists |
| Supplier webapp → admin inventory grid | Supplier UI was a separate 2002 app for one table's upkeep | Grow the fulfillment service's API/UI if supplier workflows return |
| UI tests → build-verified only | Thin presentation over a tested API; component tests would re-assert the API's tests through a browser | Playwright happy-path e2e over the demo flows |
| Server-side cart → client-side | Legacy kept carts in server session; a cart is client state until checkout, and checkout re-prices server-side anyway | Persist carts server-side only if cross-device carts become a requirement |
| Shared-token internal callback | Service-to-service auth without an identity provider in local dev | mTLS or OAuth2 client-credentials between services |
