# Nexus Bank — Security Audit Report & Recommendations

**Date:** 2026-03-14
**Scope:** Full-stack (Spring Boot backend + React/TypeScript frontend)
**Classification:** CONFIDENTIAL — Internal Use Only

---

## Executive Summary

A comprehensive security audit identified **17 distinct findings** across authentication, authorization, sensitive data exposure, business logic, transport security, and security misconfiguration categories.

| Severity | Count |
|---|---|
| CRITICAL | 3 |
| HIGH | 4 |
| MEDIUM | 7 |
| LOW | 3 |

For a banking application handling real financial transactions, this risk posture is **unacceptable for production deployment** without remediation of at least the CRITICAL and HIGH items.

---

## Attack Surface Map

| Entry Point | Auth Required | Notes |
|---|---|---|
| `POST /api/auth/register` | No | Open registration, no rate limiting |
| `POST /api/auth/login` | No | Open, no rate limiting |
| `GET /api/accounts/me` | JWT | Returns account + balance |
| `POST /api/transactions/transfer` | JWT | Debits funds, triggers NIBSS |
| `POST /api/transactions/deposit` | JWT | Self-service deposit, no source validation |
| `POST /api/transactions/withdraw` | JWT | Debits user account |
| `GET /api/transactions/history` | JWT | Full transaction history |
| `GET /api/transactions/{id}` | JWT | **No ownership check — IDOR** |
| `GET /api/external/nibss/resolve` | JWT | Account resolution |
| `POST /api/webhooks/nibss/transfer-in` | HMAC only | Unauthenticated, credits money |
| `GET /actuator/metrics` | None effectively | Leaks internal metrics |
| `GET /actuator/prometheus` | None effectively | Leaks internal metrics |

---

## Findings

---

### FINDING-01 — CRITICAL: Hardcoded JWT Secret with Weak Fallback Default

**OWASP:** A02:2021 – Cryptographic Failures
**File:** `src/main/resources/application.properties`, line 19

**Evidence:**
```properties
app.jwt.secret=${JWT_SECRET:N3xu5B4nkS3cur1tyK3y2024Pr0duct10n!}
```

**Risk:** The JWT signing secret is committed to version control as a fallback. Anyone with repository access can forge valid JWTs for any user and impersonate them. The secret is also derived using `getBytes(UTF_8)` rather than Base64 decoding, reducing effective entropy.

**Proof of Concept:**
```python
import jwt, datetime
payload = {'sub': 'victim@nexus.com', 'iss': 'nexus-bank',
           'iat': datetime.datetime.utcnow(),
           'exp': datetime.datetime.utcnow() + datetime.timedelta(days=1)}
print(jwt.encode(payload, 'N3xu5B4nkS3cur1tyK3y2024Pr0duct10n!', algorithm='HS256'))
# Use forged token against /api/accounts/me, /api/transactions/*
```

**Recommendations:**
- Remove the default value entirely. Application must fail on startup if `JWT_SECRET` is absent.
- Generate the secret with a cryptographically random 32-byte value encoded as Base64.
- In `JwtService`, decode the secret: `Base64.getDecoder().decode(properties.secret())`.
- Rotate the current secret immediately and invalidate all active tokens.

---

### FINDING-02 — CRITICAL: Hardcoded Credentials Throughout Codebase

**OWASP:** A07:2021 – Identification and Authentication Failures
**Files:**
- `src/main/resources/application.properties`, lines 5–6
- `docker-compose.yml`, lines 8, 112, 125–126, 138

**Evidence:**
```properties
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:secure_password}
app.nibss.webhook-secret=${NIBSS_WEBHOOK_SECRET:nibss-webhook-secret-dev}
```
```yaml
POSTGRES_PASSWORD: secure_password
NIBSS_WEBHOOK_SECRET: nibss-webhook-secret-dev
```

**Risk:** Three categories of secrets are hardcoded and committed to version control: the PostgreSQL password, the NIBSS webhook HMAC secret, and associated Docker environment variables. The NIBSS webhook secret being known allows an attacker to forge valid webhook payloads and credit arbitrary amounts to any account (see FINDING-03).

**Recommendations:**
- Adopt Docker Secrets, HashiCorp Vault, or a cloud-native secrets manager (AWS Secrets Manager, GCP Secret Manager).
- Never commit secrets — even as fallback defaults — to version control.
- Rotate all currently exposed credentials immediately.
- Add `.env` to `.gitignore` and use it for local dev secrets only.

---

### FINDING-03 — CRITICAL: Unauthenticated Webhook Endpoint Can Credit Arbitrary Funds

**OWASP:** A01:2021 – Broken Access Control / A04:2021 – Insecure Design
**Files:**
- `src/main/java/com/nexus/core/external/nibss/NibssWebhookController.java`, lines 33–41
- `src/main/java/com/nexus/core/external/nibss/NibssWebhookServiceImpl.java`, lines 51–75
- `src/main/java/com/nexus/core/auth/internal/SecurityConfig.java`, line 50

**Evidence:**
```java
// SecurityConfig.java
.requestMatchers("/api/webhooks/nibss/**").permitAll()

// NibssTransferInRequest — no amount validation
record NibssTransferInRequest(String nipSessionId, BigDecimal amount, ...)
```

**Risk:** The NIBSS `transfer-in` webhook is publicly accessible. Because the HMAC secret is hardcoded and committed to version control (FINDING-02), the signature check provides no real protection. An attacker can extract the secret from the repo, craft a payload crediting their account with an arbitrarily large `amount`, and POST it to `/api/webhooks/nibss/transfer-in`.

**Proof of Concept:**
```bash
SECRET="nibss-webhook-secret-dev"
BODY='{"nipSessionId":"FAKE-SESSION","amount":1000000,"beneficiaryAccountNumber":"0123456789","originatorAccountName":"Attacker","originatorBankCode":"999","originatorAccountNumber":"9876543210"}'
SIG="sha256=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | cut -d' ' -f2)"
curl -X POST http://target/api/webhooks/nibss/transfer-in \
  -H "Content-Type: application/json" \
  -H "X-Nibss-Signature: $SIG" \
  -d "$BODY"
```

**Recommendations:**
- Enforce IP allowlisting for the webhook endpoint (restrict to known NIBSS IP ranges only).
- Add `@Positive` and a configurable `@DecimalMax` to `NibssTransferInRequest.amount`.
- Enforce idempotency on `nipSessionId` — verify a session ID has not been processed before with a different amount.
- After fixing FINDING-02, rotate the NIBSS secret.

---

### FINDING-04 — HIGH: No Rate Limiting on Authentication Endpoints

**OWASP:** A07:2021 – Identification and Authentication Failures
**Files:**
- `src/main/java/com/nexus/core/auth/internal/SecurityConfig.java`
- `src/main/java/com/nexus/core/auth/AuthController.java`

**Risk:** `/api/auth/login` and `/api/auth/register` have no rate limiting, no account lockout, and no brute-force protection. Distinct error messages from `EmailAlreadyExistsException` (registration) and `UserNotFoundException` (login) enable an enumeration-then-brute-force attack chain.

**Proof of Concept:**
```bash
# Enumerate valid accounts (409 = exists, 201 = new)
for email in $(cat wordlist_emails.txt); do
  curl -s -o /dev/null -w "%{http_code} $email\n" -X POST http://target/api/auth/register \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"P@ssword1\",\"firstName\":\"A\",\"lastName\":\"B\"}"
done

# Brute force a known account
hydra -l victim@bank.com -P rockyou.txt http-post-form \
  "/api/auth/login:email=^USER^&password=^PASS^:Invalid email or password"
```

**Recommendations:**
- Integrate Bucket4j or Resilience4j rate limiting on `/api/auth/**` endpoints.
- Lock accounts after N consecutive failed attempts (with unlock via email confirmation).
- Use uniform error messages for all authentication failures (do not distinguish "user not found" from "wrong password").
- Consider adding CAPTCHA for registration.

---

### FINDING-05 — HIGH: Broken Object-Level Authorization on Transaction Lookup (IDOR)

**OWASP:** A01:2021 – Broken Access Control
**File:** `src/main/java/com/nexus/core/transaction/TransactionController.java`, lines 89–92

**Evidence:**
```java
@GetMapping("/{id}")
public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID id) {
    return ResponseEntity.ok(transactionService.getTransactionById(id));
}
```

**Risk:** Any authenticated user can fetch any transaction by UUID without ownership verification. This exposes `fromAccountNumber`, `toAccountNumber`, `amount`, `targetAccountName`, `targetBankCode`, and `description` for any user's transaction.

**Proof of Concept:**
```bash
TOKEN=$(curl -s -X POST http://target/api/auth/login \
  -d '{"email":"attacker@x.com","password":"P@ss1234"}' | jq -r .accessToken)
curl -H "Authorization: Bearer $TOKEN" http://target/api/transactions/<victim-uuid>
```

**Recommendations:**
- In `TransactionController.getTransaction()`, inject `@AuthenticationPrincipal User user`.
- Resolve the authenticated user's account number.
- Verify `transaction.fromAccountNumber == userAccountNumber || transaction.toAccountNumber == userAccountNumber` before returning.
- Return `403 Forbidden` (not `404`) if the transaction exists but does not belong to the caller.

---

### FINDING-06 — HIGH: No Validation on Financial Amounts — Negative Amount Attack

**OWASP:** A04:2021 – Insecure Design / Business Logic Flaw
**Files:**
- `src/main/java/com/nexus/core/common/TransactionRequest.java`, line 9
- `src/main/java/com/nexus/core/external/nibss/NibssWebhookServiceImpl.java`

**Evidence:**
```java
// TransactionRequest.java — only covers public API
@NotNull @DecimalMin("0.01") BigDecimal amount,

// NibssTransferInRequest — zero validation annotations
record NibssTransferInRequest(String nipSessionId, BigDecimal amount, ...)
```

**Risk:** The internal `NibssTransferInRequest` record has no `amount` validation. A crafted webhook with `amount: -1000000` would call `accountService.credit(accountNumber, new BigDecimal("-1000000"))`, which subtracts one million from the beneficiary's balance because `balance.add(-1000000)` is a deduction. Additionally, there is no upper-bound on deposit amounts.

**Recommendations:**
- Add `@Positive` and `@DecimalMax` (configurable ceiling) to all amount fields in all DTOs and records, including `NibssTransferInRequest`.
- Add a runtime guard in `AccountServiceImpl.credit()`: `if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(...)`.
- Define and enforce a configurable maximum single-transaction limit (e.g., via application properties).

---

### FINDING-07 — HIGH: JWT Not Invalidated on Logout — No Token Revocation

**OWASP:** A07:2021 – Identification and Authentication Failures
**Files:**
- `nexus-bank-ui/src/services/authService.ts`, lines 15–18
- `src/main/java/com/nexus/core/auth/internal/JwtService.java`

**Evidence:**
```typescript
logout() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  // No server-side invalidation
}
```

**Risk:** Logout is purely client-side. The JWT remains cryptographically valid for its full 24-hour lifetime after logout. An attacker who has captured a token (via XSS, logs, or network) can continue using it indefinitely. A user who reports device theft has no recourse.

**Recommendations:**
- Implement a server-side token blocklist using Redis. Add the JWT `jti` claim at logout; check it in `JwtAuthenticationFilter` on every request.
- Add a `jti` (JWT ID) claim using `UUID.randomUUID()` in `JwtService.generateToken()`.
- Alternatively, reduce token lifetime to 15 minutes and implement rotating refresh tokens.
- Expose a `POST /api/auth/logout` endpoint that adds the `jti` to the blocklist.

---

### FINDING-08 — MEDIUM: Sensitive Financial Data Logged in Plain Text

**OWASP:** A09:2021 – Security Logging and Monitoring Failures
**File:** `src/main/java/com/nexus/core/external/nibss/NibssServiceImpl.java`, lines 46, 87

**Evidence:**
```java
log.info("Resolving account: {} in bank: {}", accountNumber, bankCode);
log.info("Sending inter-bank transfer request to NIBSS: {}", bodyJson);  // full payload
```

**Risk:** Account numbers and full transfer payloads (containing `fromAccountNumber`, `toAccountNumber`, `amount`, `targetAccountName`) are logged at `INFO` level. These are routed to OpenTelemetry, Jaeger, and Grafana — some of which are exposed without authentication in the Docker stack.

**Recommendations:**
- Mask account numbers in all log statements (show only last 4 digits: `****6789`).
- Never log full financial request/response payloads. Log only correlation IDs and status codes.
- Audit all `log.info` / `log.debug` calls across `transaction/` and `external/nibss/` packages.
- Ensure the observability stack (Jaeger port 16686, OTel port 4318) is not exposed publicly.

---

### FINDING-09 — MEDIUM: Actuator Metrics and Prometheus Endpoints Exposed Without Authentication

**OWASP:** A05:2021 – Security Misconfiguration
**File:** `src/main/resources/application.properties`, line 27

**Evidence:**
```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
```

**Risk:** The `metrics`, `prometheus`, and `modulith` actuator endpoints expose JVM internals, HTTP request counts, Kafka consumer lag, database pool metrics, and the full Spring Modulith application graph — useful reconnaissance for an attacker.

**Recommendations:**
- Remove `metrics` and `prometheus` from `exposure.include`, or secure them behind an `ACTUATOR` role.
- Bind the management server to a separate non-public port: `management.server.port=9091`.
- Ensure actuator and observability stack ports are not externally accessible in production.
- Remove the `spring-modulith-actuator` dependency from production builds.

---

### FINDING-10 — MEDIUM: JWT Stored in localStorage — XSS Token Theft Vector

**OWASP:** A02:2021 – Cryptographic Failures / A03:2021 – Injection
**Files:**
- `nexus-bank-ui/src/hooks/useAuth.tsx`, line 27
- `nexus-bank-ui/src/services/api.ts`, line 8

**Evidence:**
```typescript
// Token written to localStorage on login
localStorage.setItem('token', response.accessToken);

// Token read from localStorage for every request
const token = localStorage.getItem('token');
```

**Risk:** Tokens in `localStorage` are accessible to any JavaScript running on the page. A single XSS vulnerability — in this application, a third-party npm package, or a browser extension — provides full account takeover for the 24-hour token lifetime (compounded by the missing revocation in FINDING-07).

**Recommendations:**
- Store the JWT in a `Secure; HttpOnly; SameSite=Strict` cookie managed by the backend.
- Remove all `localStorage.getItem/setItem` calls for the token.
- The Axios instance in `api.ts` will then rely on cookies automatically (`withCredentials: true`).
- Re-enable CSRF protection simultaneously (see FINDING-13).

---

### FINDING-11 — MEDIUM: Self-Service Deposit Endpoint With No Source Validation

**OWASP:** A04:2021 – Insecure Design / Business Logic Flaw
**File:** `src/main/java/com/nexus/core/transaction/TransactionController.java`, lines 47–63

**Risk:** Any authenticated user can call `POST /api/transactions/deposit` and credit their own account with any amount ≥ 0.01 NGN. There is no maximum deposit limit and no verification that funds are coming from a real source. This allows unlimited self-crediting.

**Recommendations:**
- Remove the self-service deposit endpoint from the public API entirely.
- Restrict it to an `ADMIN` or `SYSTEM` role if it must exist.
- Real deposits should only arrive via the authenticated NIBSS `transfer-in` webhook or an explicitly authorized admin operation.

---

### FINDING-12 — MEDIUM: Race Condition in Balance Check and Debit (TOCTOU)

**OWASP:** A04:2021 – Insecure Design
**File:** `src/main/java/com/nexus/core/account/internal/AccountServiceImpl.java`, lines 57–65

**Evidence:**
```java
@Transactional
public void debit(String accountNumber, BigDecimal amount) {
    Account account = getAccountByNumber(accountNumber);   // SELECT
    if (account.getBalance().compareTo(amount) < 0) { throw ... }
    account.setBalance(account.getBalance().subtract(amount)); // UPDATE
    accountRepository.save(account);
}
```

**Risk:** The `@Version` optimistic lock will throw `OptimisticLockingFailureException` on concurrent updates, but this exception is not caught in `TransactionServiceImpl.transfer()`, causing unhandled 500 errors. Additionally, `debit` and `credit` in an internal transfer execute in separate `@Transactional` method scopes, creating a window where the sender is debited but the receiver has not yet been credited.

**Recommendations:**
- Explicitly catch `OptimisticLockingFailureException` in `TransactionServiceImpl` and return a `409 Conflict` or retry.
- Ensure the `debit` and `credit` calls for internal transfers execute within a single database transaction boundary.
- Consider using `SELECT FOR UPDATE` (pessimistic locking) for the balance update query.

---

### FINDING-13 — MEDIUM: CSRF Disabled Globally

**OWASP:** A01:2021 – Broken Access Control
**File:** `src/main/java/com/nexus/core/auth/internal/SecurityConfig.java`, line 46

**Evidence:**
```java
.csrf(AbstractHttpConfigurer::disable)
```

**Risk:** CSRF is disabled globally. If FINDING-10 is remediated by migrating to `HttpOnly` cookies, this becomes an active CSRF vulnerability that allows malicious sites to trigger financial transactions on behalf of logged-in users.

**Recommendations:**
- When migrating to cookie-based auth, re-enable CSRF with `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
- Include the CSRF token in Axios request headers via the `api.ts` interceptor.

---

### FINDING-14 — MEDIUM: Missing HTTP Security Response Headers

**OWASP:** A05:2021 – Security Misconfiguration
**File:** `src/main/java/com/nexus/core/auth/internal/SecurityConfig.java`

**Risk:** The application does not set `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security`, or `Referrer-Policy`. This enables clickjacking, MIME sniffing, and referrer-based information disclosure (account numbers can leak in Referer headers to third-party analytics).

**Recommendations:**
Add to `SecurityFilterChain`:
```java
.headers(headers -> headers
    .frameOptions(f -> f.deny())
    .contentTypeOptions(Customizer.withDefaults())
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; object-src 'none';"))
    .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
)
```

---

### FINDING-15 — MEDIUM: Exception Messages Leak Internal Implementation Details

**OWASP:** A09:2021 – Security Logging and Monitoring Failures
**File:** `src/main/java/com/nexus/core/common/GlobalExceptionHandler.java`, line 83

**Evidence:**
```java
ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
// ex.getMessage() returns strings like: "Account not found: 0123456789"
```

**Risk:** Raw exception messages are returned verbatim in API responses, confirming the existence of specific account numbers and transaction IDs to any caller. This enables account enumeration and leaks internal identifiers.

**Recommendations:**
- Replace `ex.getMessage()` with sanitized, generic messages in the `RuntimeException` handler.
- Use specific `@ExceptionHandler` methods for each custom exception type.
- Log the full stack trace server-side while returning only a generic message to the client.

---

### FINDING-16 — LOW: Weak Password Policy — Minimum Length Only

**OWASP:** A07:2021 – Identification and Authentication Failures
**File:** `src/main/java/com/nexus/core/auth/dto/RegisterRequest.java`, lines 17–19

**Evidence:**
```java
@NotBlank(message = "Password is required")
@Size(min = 8, message = "Password must be at least 8 characters")
String password
```

**Risk:** Users can register with `password`, `12345678`, or `aaaaaaaa`. No complexity requirements exist, compounding the brute-force risk in FINDING-04.

**Recommendations:**
- Add a `@Pattern` constraint requiring at least one uppercase letter, one digit, and one special character.
- Alternatively, integrate a strength-estimation library (e.g., zxcvbn) and reject passwords below a minimum score.

---

### FINDING-17 — LOW: Kafka Broker Exposed Without Authentication

**OWASP:** A05:2021 – Security Misconfiguration
**File:** `docker-compose.yml`, lines 37–38

**Evidence:**
```yaml
KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
ports:
  - "9092:9092"
```

**Risk:** The Kafka broker uses `PLAINTEXT` on all listeners with the host port mapped externally. Spring Modulith externalises `TransactionProcessedEvent` (containing `transactionId`, `targetBankCode`, `nipSessionId`) to Kafka. Any process on the host can consume this topic, read transaction events, and potentially replay them on a cloud-hosted server.

**Recommendations:**
- Enable SASL/SCRAM authentication on the Kafka broker.
- Remove the `9092` host port mapping in production — inter-container communication over the `backend` network is sufficient.
- Enable TLS for Kafka in transit.
- Do not expose Kafka UI (port 8081) on a public interface.

---

## Summary Table

| # | Severity | Title | File(s) |
|---|---|---|---|
| 01 | CRITICAL | Hardcoded JWT secret with weak fallback | `application.properties:19` |
| 02 | CRITICAL | Hardcoded DB password and NIBSS secret | `application.properties:5-6`, `docker-compose.yml` |
| 03 | CRITICAL | Unauthenticated webhook can credit arbitrary funds | `NibssWebhookController.java:33-41`, `SecurityConfig.java:50` |
| 04 | HIGH | No rate limiting on auth endpoints | `SecurityConfig.java`, `AuthController.java` |
| 05 | HIGH | IDOR on transaction lookup — no ownership check | `TransactionController.java:89-92` |
| 06 | HIGH | No validation on financial amounts — negative amount attack | `TransactionRequest.java:9`, `NibssTransferInRequest` |
| 07 | HIGH | JWT not invalidated on logout — no token revocation | `authService.ts:15-18`, `JwtService.java` |
| 08 | MEDIUM | Sensitive financial data logged in plain text | `NibssServiceImpl.java:46,87` |
| 09 | MEDIUM | Actuator metrics and Prometheus exposed without auth | `application.properties:27` |
| 10 | MEDIUM | JWT in localStorage — XSS token theft vector | `useAuth.tsx:27`, `api.ts:8` |
| 11 | MEDIUM | Self-service deposit with no source validation | `TransactionController.java:47-63` |
| 12 | MEDIUM | Race condition in balance debit — TOCTOU | `AccountServiceImpl.java:57-65` |
| 13 | MEDIUM | CSRF disabled globally | `SecurityConfig.java:46` |
| 14 | MEDIUM | Missing HTTP security response headers | `SecurityConfig.java` |
| 15 | MEDIUM | Exception messages leak internal implementation details | `GlobalExceptionHandler.java:83` |
| 16 | LOW | Weak password policy — minimum length only | `RegisterRequest.java:17-19` |
| 17 | LOW | Kafka broker exposed without authentication | `docker-compose.yml:37-38` |

---

## Remediation Roadmap

### Immediate — Block Production Deployment Until Resolved
1. **FINDING-01** — Remove JWT secret fallback. Require env var at startup.
2. **FINDING-02** — Rotate all credentials. Adopt a secrets manager. Remove secrets from VCS.
3. **FINDING-03** — IP allowlist the NIBSS webhook. Add amount validation. Enforce `nipSessionId` idempotency.
4. **FINDING-05** — Add ownership check to `GET /api/transactions/{id}`.
5. **FINDING-06** — Add `@Positive` and `@DecimalMax` to all financial DTOs including `NibssTransferInRequest`.

### Short-Term — Next Sprint
6. **FINDING-04** — Rate limiting + account lockout on `/api/auth/**`.
7. **FINDING-07** — JWT blocklist in Redis for logout revocation.
8. **FINDING-10** — Migrate JWT storage to `HttpOnly` cookie.
9. **FINDING-11** — Restrict or remove the self-service deposit endpoint.
10. **FINDING-12** — Catch `OptimisticLockingFailureException`. Unify debit+credit in one transaction.

### Medium-Term — Within One Quarter
11. **FINDING-08** — Mask PII in all log statements.
12. **FINDING-09** — Restrict actuator endpoints to internal port / `ACTUATOR` role.
13. **FINDING-13** — Re-enable CSRF when migrating to cookie-based auth.
14. **FINDING-14** — Add security response headers in `SecurityConfig`.
15. **FINDING-15** — Sanitize exception messages returned in API responses.
16. **FINDING-16** — Add password complexity constraint.
17. **FINDING-17** — Enable Kafka SASL/SCRAM. Remove host port mapping.
