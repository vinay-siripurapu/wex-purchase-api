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
- Docker (for local MySQL via Testcontainers / docker-compose)
- Git (for pre-commit hooks)
- AWS Aurora MySQL (production)

---

## Quick Start

```bash
# 1. Clone and navigate
cd wex-purchase-api

# 2. Install the pre-commit hook (one-time per clone)
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit

# 3. Start a local MySQL instance
docker run --name wex-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=purchasedb \
  -e MYSQL_USER=wex_user \
  -e MYSQL_PASSWORD=wex_pass \
  -p 3306:3306 -d mysql:8.0

# 4. Build, lint, test, and check coverage
#    (integration tests use Testcontainers — no manual DB setup needed for tests)
mvn clean verify

# 5. Start the server (local profile)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

| URL | Description |
|---|---|
| `http://localhost:8080` | API base |
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | Live OpenAPI JSON |
| `http://localhost:8080/actuator/health` | Health check (AWS ALB / ECS) |

---

## Database

The application uses **AWS Aurora MySQL** in production. Schema is managed by **Flyway** — migrations run automatically on startup.

### Profiles

| Profile | Database | How to activate |
|---|---|---|
| *(default)* | Aurora MySQL via `${DB_URL}` env var | Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| `local` | Local MySQL on `localhost:3306` | `SPRING_PROFILES_ACTIVE=local` |
| `test` | Testcontainers MySQL (Docker, auto-managed) | Used automatically by `mvn test` |

### Flyway Migrations

```bash
# Migrations run automatically on startup.
# To run manually against a target DB:
mvn flyway:migrate -Dflyway.url=jdbc:mysql://host:3306/purchasedb \
                   -Dflyway.user=wex_user \
                   -Dflyway.password=secret

# Check migration status
mvn flyway:info
```

Migration scripts: `src/main/resources/db/migration/`

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
  "transactionDate": "2024-06-15T14:30:00",
  "purchaseAmountUsd": 99.99
}
```

| Field | Type | Rules |
|---|---|---|
| `Idempotency-Key` | Header (string) | Required, max 64 chars |
| `description` | string | Required, max 50 characters |
| `transactionDate` | datetime | Required, `YYYY-MM-DDTHH:MM:SS` (time component required) |
| `purchaseAmountUsd` | decimal | Required, non-negative, max 2 decimal places; `99.99` = $99.99, `0.00` = $0.00 |

Response `201 Created` (new) / `200 OK` (duplicate key replayed):
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office supplies",
  "transactionDate": "2024-06-15T14:30:00",
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
  "transactionDate": "2024-06-15T14:30:00",
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
# Run all tests (unit + controller + integration via Testcontainers)
mvn test

# Run tests + coverage report + coverage threshold check
mvn verify
```

> Integration tests require Docker running locally (Testcontainers pulls `mysql:8.0` automatically).

### Code Coverage

```bash
# Generate HTML coverage report (also enforces 80% threshold)
mvn verify

# Open report
open target/site/jacoco/index.html        # macOS
xdg-open target/site/jacoco/index.html   # Linux
```

Override threshold temporarily:
```bash
mvn verify -Djacoco.line.coverage.minimum=0.0
```

### Linter (Checkstyle)

```bash
# Check for violations
mvn checkstyle:check

# Generate HTML report without failing
mvn checkstyle:checkstyle
open target/site/checkstyle.html
```

### Pre-commit Hook

```bash
# Install (once per clone)
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit

# Bypass (exceptional cases only)
git commit --no-verify -m "your message"
```

### Swagger UI

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
open http://localhost:8080/swagger-ui.html
```

---

## Production Deployment (AWS)

- **Database**: Aurora MySQL cluster — set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` via ECS task environment or AWS Secrets Manager.
- **Schema**: Flyway runs on startup — ensure the DB user has `CREATE`, `ALTER`, `INSERT`, `SELECT`, `UPDATE`, `DELETE` on the schema.
- **Health check**: `GET /actuator/health` — returns `{"status":"UP"}` when DB is reachable. Use as ALB target group health check path.
- **H2 console**: Not present — removed entirely.
