# WEX Purchase Transaction API

A production-ready REST API to store purchase transactions in USD and retrieve them converted to any currency supported by the [US Treasury Reporting Rates of Exchange](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange).

| Document | Description |
|---|---|
| 📄 [Technical Specification](./TECH_SPEC.md) | Detailed design, architecture, business logic, and testing strategy |
| 📘 [OpenAPI 3 Spec](./docs/openapi.yaml) | Machine-readable API contract (import into Postman, Insomnia, etc.) |
| 🗂 [Data Model Diagram](./docs/datamodel.mermaid) | Entity-relationship diagram of the database schema |

---

## Requirements

- Java 21+
- Maven 3.8+
- Git (for pre-commit hooks)

---

## Quick Start

```bash
# 1. Clone and navigate
cd wex-purchase-api

# 2. Install pre-commit hook (one-time setup)
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit

# 3. Build, lint, test, and check coverage
mvn clean verify

# 4. Start the server
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/v3/api-docs`
H2 Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:purchasedb`, no password)

---

## API Reference

### 1. Store a Purchase Transaction

**`POST /api/v1/transactions`**

Requires an `Idempotency-Key` header. Repeating the same key returns the original response with `200 OK` — no duplicate is created.

```http
POST /api/v1/transactions
Content-Type: application/json
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

{
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountCents": 9999
}
```

| Field | Type | Rules |
|---|---|---|
| `Idempotency-Key` | Header (string) | Required, max 64 chars |
| `description` | string | Required, max 50 characters |
| `transactionDate` | date | Required, `YYYY-MM-DD` |
| `purchaseAmountCents` | long | Required, non-negative; `9999` = $99.99, `0` = $0.00 |

Response `201 Created` (new) / `200 OK` (duplicate key replayed):
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 99.99
}
```

---

### 2. Retrieve a Transaction in a Target Currency

**`GET /api/v1/transactions/{id}?currency={countryCurrencyDesc}`**

| Parameter | Description |
|---|---|
| `id` | UUID returned when the transaction was created |
| `currency` | Country-currency label from Treasury API, e.g. `Canada-Dollar` |

Response `200 OK`:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 99.99,
  "targetCurrency": "Canada-Dollar",
  "exchangeRate": 1.35,
  "convertedAmount": 134.99
}
```

Common currency labels: `Canada-Dollar`, `Euro Zone-Euro`, `Japan-Yen`, `United Kingdom-Pound`, `Australia-Dollar`, `India-Rupee`.

---

## Error Responses

```json
{
  "timestamp": "2024-06-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Missing required header: Idempotency-Key."
}
```

| Status | Scenario |
|---|---|
| `400` | Missing/invalid `Idempotency-Key`, or request body validation failure |
| `404` | Transaction UUID not found |
| `422` | No exchange rate available within 6 months of purchase date |
| `500` | Unexpected server error |

---

## Developer Commands

### Tests

```bash
# Run all tests (unit + controller + integration)
mvn test

# Run tests + coverage report + coverage threshold check
mvn verify
```

### Code Coverage

```bash
# Generate HTML coverage report
mvn verify

# Open report (macOS)
open target/site/jacoco/index.html

# Open report (Linux)
xdg-open target/site/jacoco/index.html
```

Coverage report is at `target/site/jacoco/index.html`. The build fails if line coverage drops below **80%**.

To override the threshold temporarily (e.g. during prototyping):
```bash
mvn verify -Djacoco.line.coverage.minimum=0.0
```

### Linter (Checkstyle)

```bash
# Check for style violations (fails build on errors)
mvn checkstyle:check

# Generate HTML style report without failing
mvn checkstyle:checkstyle

# Open report (macOS)
open target/site/checkstyle.html
```

Checkstyle enforces:
- No tab characters (spaces only)
- Max line length: 120 characters
- No star imports or unused imports
- Standard Java naming conventions
- Required braces on all blocks
- `equals()` and `hashCode()` paired

Style config: [`checkstyle.xml`](./checkstyle.xml)

### Pre-commit Hook

Install once per clone:
```bash
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
```

The hook runs automatically before every `git commit` and executes:
1. `mvn checkstyle:check` — linter
2. `mvn verify` — tests + 80% coverage check

To bypass in exceptional cases:
```bash
git commit --no-verify -m "your message"
```

### Swagger UI

With the server running (`mvn spring-boot:run`):

- Interactive UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Static YAML spec: [`docs/openapi.yaml`](./docs/openapi.yaml)

The static YAML can be imported into **Postman**, **Insomnia**, or any OpenAPI-compatible tool.

---

## Data Model

```
┌──────────────────────────────────────────────────────────┐
│                   purchase_transactions                  │
├─────────────────┬─────────────┬────────────────────────  ┤
│ id              │ VARCHAR(36) │ PK — UUID, auto-generated │
│ idempotency_key │ VARCHAR(64) │ UK — unique per request   │
│ description     │ VARCHAR(50) │ Not null                  │
│ transaction_date│ DATE        │ Not null                  │
│ purchase_amount │ DECIMAL(17,2│ Not null, stored in USD   │
└─────────────────┴─────────────┴───────────────────────── ┘
```

Full ERD: [`docs/datamodel.mermaid`](./docs/datamodel.mermaid)

---

## Production Notes

- **Database**: H2 in-memory by default. Swap to PostgreSQL via `application.properties` — no code changes required.
- **H2 Console**: Disable in production: `spring.h2.console.enabled=false`.
- **Timeouts**: Treasury API timeouts configurable via `treasury.api.connect-timeout-ms` and `treasury.api.read-timeout-ms`.
- **Coverage threshold**: Adjust `jacoco.line.coverage.minimum` in `pom.xml` (currently `0.80`).
