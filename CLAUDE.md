# Nexus Bank — Claude Code Instructions

## Project Overview

A full-stack banking application with an inter-bank transfer system (simulated NIBSS NIP integration). Backend is a **Spring Modulith** monolith; frontend is **React + TypeScript + Vite**.

```
nexus-bank/
├── src/                    # Spring Boot backend
├── nexus-bank-ui/          # React frontend
├── docker/                 # Infra config (Prometheus, OTel, Grafana)
├── docker-compose.yml      # Production stack
├── docker-compose.dev.yml  # Dev override (hot-reload)
├── Dockerfile              # Production multi-stage build
└── Dockerfile.dev          # Dev image (mvn spring-boot:run)
```

---

## Technology Stack

### Backend
| Tech | Version |
|---|---|
| Java | 17 |
| Spring Boot | 4.0.2 |
| Spring Modulith | 2.0.2 |
| Spring Security | 7.x |
| PostgreSQL | 16 |
| Kafka | 7.6.0 (KRaft, single broker) |
| JJWT | 0.13.0 |
| Lombok | (annotation processor) |

### Frontend
| Tech | Version |
|---|---|
| React | 19.2 |
| TypeScript | 5.9 |
| Vite | 7.3 |
| Tailwind CSS | 4.2 |
| React Router DOM | 7.13 |
| Axios | 1.13 |

---

## Common Commands

### Backend
```bash
# Run tests
mvn test

# Run a single test class
mvn test -Dtest=TransactionIdempotencyTests

# Build fat JAR (skip tests)
mvn -B clean package -DskipTests

# Run locally (requires Postgres + Kafka running)
mvn spring-boot:run
```

### Frontend
```bash
cd nexus-bank-ui

npm run dev        # Vite dev server (usually :5173)
npm run build      # tsc + Vite production build
npm run lint       # ESLint
```

### Docker (full stack)
```bash
# Production stack
docker compose up --build

# Development stack (backend hot-reload)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up

# Rebuild only the app container
docker compose up --build nexus-bank-app
```

---

## Backend Architecture

### Base Package
`com.nexus.core`

### Spring Modulith Module Map

```
com.nexus.core
├── auth/                   # Authentication & JWT
│   ├── (public)            # User, Role, UserRepository, AuthService, AuthController
│   │                       # UserRegisteredEvent, UserNotFoundException, EmailAlreadyExistsException
│   ├── dto/                # @NamedInterface("dto") — RegisterRequest, LoginRequest, AuthResponse
│   └── internal/           # JwtService, JwtProperties, JwtAuthenticationFilter, SecurityConfig, AuthServiceImpl
│
├── account/                # Account management
│   ├── (public)            # Account, AccountStatus, AccountRepository, AccountService, AccountController
│   ├── dto/                # AccountResponse
│   └── internal/           # AccountServiceImpl, AccountEventListener
│
├── transaction/            # Transfers, deposits, withdrawals
│   ├── (public)            # Transaction, TransactionRepository, TransactionService
│   │                       # NibssService, TransactionProcessedEvent, TransactionController
│   └── internal/           # TransactionServiceImpl, TransactionEventListener
│
├── common/                 # @NamedInterface("dto") — shared enums/DTOs/exceptions
│   │                       # Bank, TransactionType, TransactionStatus
│   │                       # TransactionRequest, TransactionResponse, BeneficiaryResponse
│   │                       # DuplicateTransactionException, GlobalExceptionHandler
│
└── external/nibss/         # NIBSS mock — NibssServiceImpl, NibssController
```

**Module rules:**
- Classes under `internal/` are package-private and cannot be used by other modules.
- Cross-module DTOs are exposed via `@NamedInterface("dto")` in `package-info.java`.
- Modules communicate via **Spring Application Events** (`@ApplicationModuleListener`), not direct service calls.
- `UserRegisteredEvent` → account module creates a bank account automatically.
- `TransactionProcessedEvent` → Kafka-externalised event for inter-bank NIBSS flow.

---

## API Endpoints

All endpoints are prefixed with `/api`.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | No | Register user → fires UserRegisteredEvent → account auto-created |
| POST | `/auth/login` | No | Returns JWT `accessToken` |
| GET | `/accounts/me` | JWT | Authenticated user's account |
| POST | `/transactions/transfer` | JWT | Internal or inter-bank transfer |
| POST | `/transactions/deposit` | JWT | Credit account |
| POST | `/transactions/withdraw` | JWT | Debit account |
| GET | `/transactions/history` | JWT | Full transaction history for account |
| GET | `/transactions/{id}` | JWT | Single transaction |
| GET | `/external/nibss/resolve` | JWT | Resolve beneficiary (`?bankCode=&accountNumber=`) |
| GET | `/actuator/health` | No | Health check |

Error responses use **RFC 7807 ProblemDetail** format (`title`, `detail`, `type`).

---

## Key Design Patterns

### Transaction Safety
1. **Idempotency key** — unique constraint on `idempotencyKey` column; frontend sends `crypto.randomUUID()`.
2. **Semantic deduplication** — `TransactionServiceImpl` blocks identical transfers within a 30-second window.
3. **Optimistic locking** — `Account` entity uses `@Version` to prevent lost updates on concurrent balance changes.
4. **Async NIBSS processing** — inter-bank transfers respond immediately (PENDING) and process via Kafka → `TransactionEventListener`.
5. **Refund logic** — if NIBSS processing fails, the sender's balance is automatically restored.

### JWT Auth
- Stateless sessions (`SessionCreationPolicy.STATELESS`)
- Token lifetime: 24 hours (`app.jwt.expiration-ms=86400000`)
- `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`
- CORS allows all `http://localhost:[*]` origins (dev only)

---

## Spring Security 7 Breaking Change

`DaoAuthenticationProvider` no longer has a no-arg constructor. **Always** pass `UserDetailsService` in the constructor:

```java
// CORRECT (Spring Security 7 / Spring Boot 4.x)
DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService());

// WRONG — will not compile
DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
provider.setUserDetailsService(...); // method removed
```

---

## Database

PostgreSQL 16. Schema managed by Hibernate `ddl-auto=update`.

| Table | Key Fields |
|---|---|
| `users` | id, email (unique), password (BCrypt), role, created_at |
| `accounts` | id, account_number (unique, 10-digit), balance (BigDecimal), status, version, user_id |
| `transactions` | id, idempotency_key (unique), amount, type, status, from/to account, bank codes, nip_session_id, created_at |

Local dev connection: `jdbc:postgresql://localhost:5432/nexus_bank`
Docker stack connection: `jdbc:postgresql://nexus-db:5432/nexus_bank` (port 5433 on host)

---

## Kafka

KRaft mode, single broker. Spring Modulith externalises events to Kafka automatically (`spring.modulith.events.externalization.enabled=true`).

- Local: `localhost:9092`
- Docker: `nexus-kafka:9092`
- Kafka UI: `http://localhost:8081`

---

## Observability Stack (Docker)

| Service | URL | Purpose |
|---|---|---|
| Jaeger | http://localhost:16686 | Distributed traces |
| Prometheus | http://localhost:9090 | Metrics |
| Grafana | http://localhost:4040 | Dashboards |
| Kafka UI | http://localhost:8081 | Event inspection |
| OTel Collector | :4318 (HTTP), :4317 (gRPC) | Telemetry ingestion |

OTel collector pipelines:
- **traces** → Jaeger (via OTLP gRPC)
- **metrics** → Prometheus (scrapes `:8889`)

App config properties:
```properties
management.otlp.tracing.endpoint=http://otel-collector:4318/v1/traces
management.otlp.metrics.export.url=http://otel-collector:4318/v1/metrics
```
These must be **separate** properties. The metrics registry appends `/v1/metrics` to its own URL — do not reuse the tracing endpoint URL for metrics.

---

## Frontend Architecture

### Routing (`App.tsx`)
```
/           → redirect to /dashboard
/login      → LoginPage (public)
/register   → RegisterPage (public)
/dashboard  → DashboardPage (protected, inside DashboardLayout)
/transfer   → TransferPage (protected, inside DashboardLayout)
```

`ProtectedRoute` reads from `AuthContext`. If `isLoading` is true it shows a spinner; if no user it redirects to `/login`.

### Auth State (`useAuth`)
- Stored in `localStorage` (`token`, `user`)
- Loaded synchronously on mount
- Axios interceptor in `src/services/api.ts` automatically injects `Authorization: Bearer <token>`

### Tailwind CSS v4 Setup
- Uses `@tailwindcss/vite` plugin (NOT the old PostCSS plugin)
- Custom colours defined in `src/index.css` via `@theme {}`:
  ```css
  --color-navy: #001f3f
  --color-navy-light: #003366
  --color-navy-dark: #001a33
  --color-gold: #D4AF37
  --color-gold-light: #F9E076
  --color-gold-dark: #B8860B
  ```
- Do **not** add a `tailwind.config.js` — it is not used in v4.
- Do **not** use `@tailwind base/components/utilities` — use `@import "tailwindcss"` instead.

### TypeScript Strict Mode
`verbatimModuleSyntax` is enabled. All type-only imports **must** use `import type`:
```typescript
// CORRECT
import type { Account, Transaction } from '../types';

// WRONG — will fail tsc
import { Account, Transaction } from '../types';
```

---

## Development Workflow

### Hot-Reload (Backend in Docker)
```bash
# Terminal 1 — start dev stack
docker compose -f docker-compose.yml -f docker-compose.dev.yml up

# Terminal 2 — trigger a reload after editing Java files
mvn compile
# DevTools detects changed .class files and restarts the app (~1–2s)
```
DevTools polling is tuned in `src/main/resources/application-dev.properties`.

### Local Backend (no Docker)
Requires Postgres on `:5432` and Kafka on `:9092` (or override via env vars).
```bash
mvn spring-boot:run
```

### Frontend
```bash
cd nexus-bank-ui
npm run dev   # http://localhost:5173
```
API calls proxy to `http://localhost:8080/api` (set directly in `src/services/api.ts`).

---

## Testing

Tests use H2 in-memory database and Spring Modulith test support.

| Test Class | Covers |
|---|---|
| `ModularityTests` | Spring Modulith module structure validation |
| `AccountIntegrationTests` | Account creation via UserRegisteredEvent |
| `TransactionIntegrationTests` | Deposit, withdraw, transfer flows |
| `TransactionIdempotencyTests` | Duplicate request rejection via idempotency key |
| `TransactionSemanticDeduplicationTests` | 30-second window dedup logic |

Run all: `mvn test`

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/nexus_bank` | DB connection |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `secure_password` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker |
| `JWT_SECRET` | (hardcoded default — change in prod) | JWT signing key |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` | Tracing endpoint |
| `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` | `http://localhost:4318/v1/metrics` | Metrics endpoint |
| `SPRING_PROFILES_ACTIVE` | (none) | Set to `dev` for DevTools |
