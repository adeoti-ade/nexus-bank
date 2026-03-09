# 🔥 The Failure Scenarios For Simulations

---

## 1️⃣ Duplicate & Idempotency Failures (most important)

### Scenario A — client retries due to timeout

**Simulate**

1. Send a transfer request
2. Artificially delay your API response
3. Send the same request again with the same idempotency token

**You must verify**

* only one ledger entry exists
* only one async NIBSS call is triggered
* second request returns the original result

If not — you have a financial duplication bug.

---

### Scenario B — same transaction, different idempotency token

Users retry from another device or app bug.

**Simulate**

* same sender, receiver, amount, reference
* different idempotency token

**You must decide**
Should this be allowed or blocked?

This tests your **semantic idempotency**, not token idempotency.

That’s senior-level thinking.

---

### Scenario C — race condition (true fintech killer)

**Simulate**
Fire 5–10 identical requests concurrently.

**Watch for**

* double debits
* negative balances
* lost updates
* inconsistent ledger vs wallet balance

If this breaks, your DB isolation or locking strategy is wrong.

---

## 2️⃣ Async External Dependency Failures (NIBSS reality)

External switches fail. Constantly.

---

### Scenario D — NIBSS timeout

**Simulate**

* async call never returns

**Your system must**

* keep transaction in PENDING
* retry safely
* avoid duplicate debit on retry
* expose retry visibility in metrics

---

### Scenario E — NIBSS success but callback never arrives

Very common in real systems.

**Simulate**

* outbound request succeeds
* callback never comes

**Verify**

* reconciliation job detects it
* transaction doesn’t stay pending forever
* manual intervention path exists

---

### Scenario F — duplicate callback from NIBSS

Switches resend callbacks.

**Simulate**
Call your callback endpoint twice.

**You must guarantee**

* idempotent callback handling
* no double settlement
* no state regression

If this fails, you don’t have real-world safety.

---

## 3️⃣ Partial Failure / Crash Scenarios

This is where seniors are forged.

---

### Scenario G — crash after debit, before async dispatch

**Simulate**
Kill the service after:

* wallet debited
* transaction persisted
* but before message published

**Question**
How does the async process resume?

If answer is “it doesn’t” → you need outbox pattern.

---

### Scenario H — crash after async dispatch but before response

User retries because they didn’t see response.

This tests:

* idempotency
* replay safety
* state recovery

---

### Scenario I — DB commit succeeds, response fails

Client thinks request failed.

This is a classic financial duplication trigger.

**Verify**
Retry doesn’t cause double processing.

---

## 4️⃣ Concurrency & Balance Integrity

---

### Scenario J — simultaneous spend from same wallet

**Simulate**
Two transfers draining same wallet at the same time.

**Verify**

* no overdraft unless allowed
* proper locking
* correct final balance

---

### Scenario K — reconciliation mismatch

Force ledger and wallet balance mismatch manually.

**Verify**

* detection alert exists
* reconciliation job flags it
* recovery procedure is clear

If you can’t detect this automatically, observability is incomplete.

---

## 5️⃣ Observability Validation (very overlooked)

You installed Prometheus, Grafana, Jaeger — now prove they’re useful.

---

### Scenario L — slow transaction

Artificially delay NIBSS.

**You must see**

* latency spike in Prometheus
* Grafana dashboard change
* Jaeger trace showing the slow span

If you can’t pinpoint the delay in < 2 minutes, observability isn’t working yet.

---

### Scenario M — retry storm

Simulate repeated retries.

**Watch**

* queue backlog
* retry counters
* error rates

This is how cascading failures start.

---

## 6️⃣ Data Integrity & Security Edge Cases

---

### Scenario N — replay attack attempt

Re-send old successful request with same idempotency key days later.

What happens?

Token expiry strategy gets tested.

---

### Scenario O — duplicate message consumption

If you use queues, simulate processing same message twice.

Your consumer must be idempotent.

---

# ⭐ The rule while running these tests

Don’t just check correctness.

Ask:

* Could money be lost?
* Could money be duplicated?
* Could a transaction be stuck forever?
* Could support struggle to debug this?

That mindset is what separates strong engineers from fintech engineers.

---

# The most important insight

You don’t become senior by building happy paths.

You become senior by knowing:

> **exactly how your system behaves when everything goes wrong.**

That confidence cannot be prompted into existence.

It must be simulated.

---

If you want, next I can:
✅ give you a step-by-step script to execute these tests locally
✅ design the exact metrics and alerts your observability stack should expose
✅ help you implement the outbox pattern (very likely your next evolution)
✅ design a reconciliation engine like real banks use

Tell me the next move.
