# Nexus Bank - Project Context

## Project Overview
Nexus Bank is a modern, modular banking application built with Spring Boot 4 and Java 17+, following **Spring Modulith** architectural principles. It features robust user authentication, automated account management, and a high-scale asynchronous transaction engine with built-in inter-bank transfer support via a mock NIBSS integration.

## Core Modules
- **Auth:** Handles user registration and JWT-based authentication.
- **Account:** Manages bank accounts, balances, and status. Automatically creates accounts upon user registration via events.
- **Transaction:** The core engine for transfers, deposits, and withdrawals. Implements both technical idempotency and semantic deduplication.
- **External:** Integrates with external financial systems (e.g., NIBSS NIP for inter-bank transfers).
- **Common:** Shared DTOs, Enums, and Infrastructure (Exceptions, Global Handling).

## Key Architectural Decisions

### 1. Event-Driven Modularity
We use Spring Modulith events to decouple modules. 
- `UserRegisteredEvent`: Triggers account creation in the Account module.
- `TransactionProcessedEvent`: Triggers asynchronous inter-bank transfer processing.

### 2. Transaction Safety (Double-Tap Protection)
Transactions are protected by two layers:
- **Technical Idempotency:** Client-side generated `idempotencyKey` stored in the database.
- **Semantic Deduplication:** Server-side check preventing duplicate transactions (same sender, receiver, amount, type) within a **30-second window**, provided the previous transaction is `COMPLETED` or `PENDING`.

### 3. Asynchronous Inter-bank Transfers
To ensure UI responsiveness, inter-bank transfers follow a "Fire and Forget" pattern:
1. **Synchronous:** Validate balance, debit sender, create `PROCESSING` transaction, return `202 Accepted`.
2. **Asynchronous:** `TransactionEventListener` picks up the event, calls NIBSS (mock), and updates status to `COMPLETED` or `FAILED` (with refund).

### 4. Observability Stack
Fully dockerized OpenTelemetry stack:
- **OTEL Collector:** Central hub for data routing.
- **Prometheus:** Metrics storage.
- **Jaeger:** Distributed tracing (crucial for tracking async transaction flows).
- **Grafana:** Unified visualization.

## Engineering Standards & Conventions

### Error Handling
We follow **RFC 7807 (Problem Details for HTTP APIs)**. All exceptions are handled by the `GlobalExceptionHandler` in the `common` package.

### Modularity Rules
- Shared types (DTOs, Enums that cross module boundaries) MUST reside in the `common` package.
- Modules MUST NOT have circular dependencies. Interfaces for external capabilities should be defined in the consuming module to break cycles.

### Testing
- **Modularity Tests:** Verified via `ApplicationModules.of(NexusBankApplication.class).verify()`.
- **Integration Tests:** Use `@SpringBootTest` with the `test` profile (H2 database).

## Development Lifecycle
- **Run Locally:** `./mvnw spring-boot:run`
- **Run Stack:** `docker-compose up --build`
- **Tests:** `./mvnw test`

## Current Status
- [x] Auth & Account Event Integration.
- [x] Idempotent Transactions & Semantic Deduplication.
- [x] Mock NIBSS Integration (Account Resolution & Async Transfers).
- [x] Dockerization & Observability Stack.
- [x] React + TypeScript + Tailwind UI (Classic Bank Theme).
- [ ] WebSocket Notifications (Next).
- [ ] Transfer PIN Security.
