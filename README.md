# Pet Store Modern

A migration of Java Pet Store 1.3.1 (J2EE 1.3) to a modern stack: Spring Boot 4.1.0 on Java 21, MongoDB, and Angular. The legacy EJB-based catalog, customer, order, and notification modules are being re-implemented as a Spring Boot backend backed by MongoDB documents, with a new Angular + PrimeNG front end replacing the legacy JSP storefront.

## Prerequisites

- Java 21
- Node 22
- Docker

## Run it

1. `docker compose up -d` — starts MongoDB on `localhost:27017`.
2. `./mvnw -pl core spring-boot:run` — starts the core service on `http://localhost:8080`.
3. `./mvnw -pl fulfillment-service spring-boot:run` — starts the fulfillment service on `http://localhost:8081`.
4. `cd frontend && npm ci && npm start` — starts the Angular dev server.
5. Open `http://localhost:4200`.

## Run the tests

- Backend: `./mvnw --batch-mode verify` (runs from the repo root; no MongoDB required).
- Frontend: `cd frontend && npm test -- --watch=false`.
