# Pet Store Modern

A migration of Java Pet Store 1.3.1 (J2EE 1.3) to a modern stack: Spring Boot 4.1.0 on Java 21, MongoDB, and Angular. The legacy EJB-based catalog, customer, order, and notification modules are being re-implemented as a Spring Boot backend backed by MongoDB documents, with a new Angular + PrimeNG front end replacing the legacy JSP storefront.

## Prerequisites

- Java 21
- Node 22
- Docker

## Run it

1. `docker compose up -d` — starts MongoDB on `localhost:27017`.
2. `./mvnw -pl core spring-boot:run -Dspring-boot.run.arguments=--petstore.seed=true` — starts the core service on `http://localhost:8080` and loads the legacy catalog (`Populate-UTF8.xml`) into MongoDB. Idempotent — safe to re-run (upserts by id). Omit the argument (or run again without it) once seeded; it defaults to `petstore.seed=false`.
3. `./mvnw -pl fulfillment-service spring-boot:run` — starts the fulfillment service on `http://localhost:8081`.
4. `cd frontend && npm ci && npm start` — starts the Angular dev server.
5. Open `http://localhost:4200`.

## Run the tests

- Backend: `./mvnw --batch-mode verify` (runs from the repo root). Unit tests need no
  MongoDB; the integration tests start their own throwaway MongoDB via Testcontainers
  (Docker required).

## Testing strategy

Test investment follows migration risk. The risk in this migration is **behavioral
fidelity of the backend** — did the legacy data and rules survive the move? — so that
is where the automated tests live: characterization tests derived from the legacy
application's flows (real legacy seed data, real MongoDB via Testcontainers), plus
ArchUnit tests that enforce the module boundaries and the application/migration
separation.

The Angular frontend is deliberately not unit-tested: it is a thin presentation layer
over the tested API, verified by the type-checked build in CI and by manual testing
against the legacy application's user flows. At production scale the next test
investment would be a Playwright happy-path e2e over the demo flow — not component
tests — as that yields the most confidence per line.
