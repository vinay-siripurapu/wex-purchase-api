# WEX Purchase Transaction API — Technical Specification

**Version:** 1.0.0
**Language:** Java 21
**Framework:** Spring Boot 3.2.x
**Last Updated:** 2025-05-25

---

## Table of Contents

1. [Overview](#1-overview)
2. [Requirements Summary](#2-requirements-summary)
3. [Architecture](#3-architecture)
4. [Technology Stack](#4-technology-stack)
5. [Project Structure](#5-project-structure)
6. [Data Model](#6-data-model)
7. [API Design](#7-api-design)
8. [Business Logic](#8-business-logic)
9. [External Integration — Treasury API](#9-external-integration--treasury-api)
10. [Validation Rules](#10-validation-rules)
11. [Error Handling](#11-error-handling)
12. [Testing Strategy](#12-testing-strategy)
13. [Configuration](#13-configuration)
14. [Local Setup](#14-local-setup)
15. [Design Decisions & Trade-offs](#15-design-decisions--trade-offs)

---

## 1. Overview

The WEX Purchase Transaction API is a RESTful backend service that allows clients to:

1. **Store** a purchase transaction in USD.
2. **Retrieve** a stored transaction with the purchase amount converted to a target currency, using exchange rates published by the US Treasury Reporting Rates of Exchange API.

The service is built for production readiness: validated inputs, structured error responses, full automated test coverage, and an externally configurable datasource and Treasury API URL.

---

## 2. Requirements Summary

### Requirement 1 — Store a Purchase Transaction

| Field | Type | Constraint |
|---|---|---|
| `description` | String | Required, max 50 characters |
| `transactionDate` | Date | Required, ISO format `YYYY-MM-DD` |
| `purchaseAmountCents` | Long | Required, non-negative integer (cents); e.g. `9999` = $99.99, `0` = $0.00 |
| `id` | UUID | Auto-generated on store; uniquely identifies the transaction |

### Requirement 2 — Retrieve a Transaction in a Target Currency

- Look up a stored transaction by UUID.
- Call the [US Treasury Reporting Rates of Exchange API](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange) to fetch the exchange rate for the target currency.
- Apply the exchange rate to the USD amount and return the converted value rounded to 2 decimal places.

**Exchange rate lookup rules:**
- Must use a rate with `record_date` ≤ purchase date.
- Rate must be within 6 months before the purchase date.
- If no qualifying rate exists → return an error (HTTP 422).
- No exact date match required; use the most recent available rate in the window.

---

## 3. Architecture

```
Client
  │
  ▼
┌─────────────────────────────────────────┐
│         Spring Boot Application          │
│                                          │
│  ┌──────────────────────────────────┐   │
│  │   PurchaseTransactionController  │   │  ← REST layer (validation, routing)
│  └────────────────┬─────────────────┘   │
│                   │                      │
│  ┌────────────────▼─────────────────┐   │
│  │   PurchaseTransactionService     │   │  ← Business logic, orchestration
│  └──────┬─────────────────┬─────────┘   │
│         │                 │              │
│  ┌──────▼──────┐  ┌───────▼──────────┐  │
│  │ Transaction │  │ TreasuryExchange  │  │
│  │ Repository  │  │ RateService       │  │
│  └──────┬──────┘  └───────┬──────────┘  │
│         │                 │              │
└─────────┼─────────────────┼─────────────┘
          │                 │
     ┌────▼────┐    ┌───────▼──────────────┐
     │  H2 DB  │    │  Treasury Fiscal API  │
     └─────────┘    └──────────────────────┘
```

### Layer Responsibilities

| Layer | Class | Responsibility |
|---|---|---|
| Controller | `PurchaseTransactionController` | HTTP request/response mapping, input validation delegation |
| Service | `PurchaseTransactionService` | Business logic: cents→dollars conversion, orchestrate store/retrieve |
| Service | `TreasuryExchangeRateService` | Build Treasury API query, parse response, enforce 6-month window |
| Repository | `PurchaseTransactionRepository` | JPA CRUD operations against the database |
| Model | `PurchaseTransaction` | JPA entity; UUID PK auto-generated via `@PrePersist` |
| DTOs | `CreateTransactionRequest`, `TransactionResponse`, `ConvertedTransactionResponse` | API contracts; decouple transport from domain |
| Exception | `GlobalExceptionHandler` | Centralised structured error responses |

---

## 4. Technology Stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java 21 | Required by brief; LTS release with virtual threads & records |
| Framework | Spring Boot 3.2.x | Industry standard; built-in validation, JPA, web, test slice support |
| Persistence | Spring Data JPA + H2 | Zero-config embedded DB for dev/test; swap to PostgreSQL via config |
| Validation | Jakarta Validation (`spring-boot-starter-validation`) | Declarative, annotation-driven, integrates with MockMvc error handling |
| HTTP Client | `RestTemplate` (Spring Web) | Sufficient for synchronous external calls; easily mockable in tests |
| Test — Unit | JUnit 5 + Mockito | Standard Java unit testing; full isolation via mocks |
| Test — Web | `@WebMvcTest` + MockMvc | Controller slice tests without full context startup |
| Test — Integration | `@SpringBootTest` + WireMock | Full context with external API stubbed via WireMock |
| Build | Maven 3.8+ | Standard Java build tool; reproducible dependency resolution |

---

## 5. Project Structure

```
wex-purchase-api/
├── pom.xml
├── README.md
├── TECH_SPEC.md
└── src/
    ├── main/
    │   ├── java/com/wex/purchase/
    │   │   ├── PurchaseApiApplication.java          # Entry point
    │   │   ├── config/
    │   │   │   └── AppConfig.java                   # RestTemplate bean, timeouts
    │   │   ├── controller/
    │   │   │   └── PurchaseTransactionController.java
    │   │   ├── dto/
    │   │   │   ├── CreateTransactionRequest.java     # POST request body
    │   │   │   ├── TransactionResponse.java          # POST response body
    │   │   │   ├── ConvertedTransactionResponse.java # GET response body
    │   │   │   ├── TreasuryApiResponse.java          # Treasury API wrapper
    │   │   │   └── TreasuryExchangeRateRecord.java   # Treasury API record
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── TransactionNotFoundException.java
    │   │   │   └── ExchangeRateUnavailableException.java
    │   │   ├── model/
    │   │   │   └── PurchaseTransaction.java          # JPA entity
    │   │   ├── repository/
    │   │   │   └── PurchaseTransactionRepository.java
    │   │   └── service/
    │   │       ├── PurchaseTransactionService.java
    │   │       └── TreasuryExchangeRateService.java
    │   └── resources/
    │       └── application.properties
    └── test/
        ├── java/com/wex/purchase/
        │   ├── controller/
        │   │   └── PurchaseTransactionControllerTest.java
        │   ├── integration/
        │   │   └── PurchaseTransactionIntegrationTest.java
        │   └── service/
        │       ├── PurchaseTransactionServiceTest.java
        │       └── TreasuryExchangeRateServiceTest.java
        └── resources/
            └── application-test.properties
```

---

## 6. Data Model

### Entity: `PurchaseTransaction`

**Table:** `purchase_transactions`

| Column | Java Type | DB Type | Constraints |
|---|---|---|---|
| `id` | `UUID` | `UUID` / `VARCHAR(36)` | Primary key, not null, immutable |
| `description` | `String` | `VARCHAR(50)` | Not null, max 50 chars |
| `transaction_date` | `LocalDate` | `DATE` | Not null |
| `purchase_amount` | `BigDecimal` | `DECIMAL(17, 2)` | Not null, stored in dollars |

**Notes:**
- `id` is generated in Java via `UUID.randomUUID()` inside `@PrePersist`, not by the database. This makes IDs predictable in tests and portable across DB engines.
- `purchase_amount` is always stored in **US dollars** with exactly 2 decimal places, regardless of how the client submits the value.
- The `purchaseAmountCents` field exists only in the request DTO; it is converted to dollars before persistence.

### Cents → Dollars Conversion

```
storedAmount = BigDecimal.valueOf(purchaseAmountCents) / 100
             = 9999 / 100 = 99.99
             = 0    / 100 = 0.00
             = 1    / 100 = 0.01
```

Scale is explicitly set to 2 with `RoundingMode.HALF_UP` to guarantee no precision surprises from integer division.

---

## 7. API Design

### Base URL

```
http://localhost:8080/api/v1
```

---

### POST `/api/v1/transactions` — Store a Transaction

**Request**

```http
POST /api/v1/transactions
Content-Type: application/json

{
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountCents": 9999
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `description` | `string` | Yes | Max 50 characters, not blank |
| `transactionDate` | `string` | Yes | ISO date format `YYYY-MM-DD` |
| `purchaseAmountCents` | `long` | Yes | Non-negative integer; `0` = $0.00 |

**Response — 201 Created**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 99.99
}
```

| Field | Description |
|---|---|
| `id` | Auto-generated UUID; use this to retrieve the transaction |
| `description` | Echo of the submitted description |
| `transactionDate` | Echo of the submitted date |
| `purchaseAmountUsd` | Stored dollar amount (cents input divided by 100) |

---

### GET `/api/v1/transactions/{id}?currency={countryCurrencyDesc}` — Retrieve with Conversion

**Request**

```http
GET /api/v1/transactions/a1b2c3d4-e5f6-7890-abcd-ef1234567890?currency=Canada-Dollar
```

| Parameter | Location | Required | Description |
|---|---|---|---|
| `id` | Path | Yes | UUID from the store response |
| `currency` | Query | Yes | `country_currency_desc` value from Treasury API |

**Response — 200 OK**

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

| Field | Description |
|---|---|
| `purchaseAmountUsd` | Original stored amount in USD |
| `targetCurrency` | The currency label passed in the request |
| `exchangeRate` | Rate used from the Treasury API |
| `convertedAmount` | `purchaseAmountUsd × exchangeRate`, rounded to 2 dp |

**Common Currency Labels**

| Country / Region | `currency` value |
|---|---|
| Canada | `Canada-Dollar` |
| Euro Zone | `Euro Zone-Euro` |
| Japan | `Japan-Yen` |
| United Kingdom | `United Kingdom-Pound` |
| Australia | `Australia-Dollar` |
| India | `India-Rupee` |

For the full list:
```
GET https://api.fiscaldata.treasury.gov/services/api/v1/accounting/od/rates_of_exchange
      ?fields=country_currency_desc&page[size]=200
```

---

## 8. Business Logic

### Storing a Transaction

```
1. Receive POST body
2. Validate fields (Jakarta Validation)
   - description: not blank, ≤ 50 chars
   - transactionDate: not null, valid ISO date
   - purchaseAmountCents: not null, ≥ 0
3. Convert cents to dollars:
      dollars = BigDecimal.valueOf(cents).divide(100, 2, HALF_UP)
4. Persist PurchaseTransaction entity (UUID auto-assigned)
5. Return 201 with TransactionResponse
```

### Retrieving with Currency Conversion

```
1. Look up transaction by UUID
   └── not found → 404 TransactionNotFoundException

2. Query Treasury API for exchange rate:
   - filter: country_currency_desc = targetCurrency
   - filter: record_date ≤ purchaseDate
   - filter: record_date ≥ purchaseDate - 6 months
   - sort: record_date DESC
   - page size: 1  (only need the most recent)
   └── empty result → 422 ExchangeRateUnavailableException
   └── API error   → 422 ExchangeRateUnavailableException

3. Calculate converted amount:
      converted = purchaseAmountUsd × exchangeRate
      converted = converted.setScale(2, HALF_UP)

4. Return 200 with ConvertedTransactionResponse
```

### Currency Conversion Example

```
purchaseAmountUsd = 100.00
exchangeRate      = 1.3500   (Canada-Dollar, record_date: 2024-06-01)
converted         = 100.00 × 1.3500 = 135.00

purchaseAmountUsd = 10.00
exchangeRate      = 1.2345
converted         = 10.00 × 1.2345 = 12.345 → rounded to 12.35
```

---

## 9. External Integration — Treasury API

**Base URL:**
```
https://api.fiscaldata.treasury.gov/services/api/v1/accounting/od/rates_of_exchange
```

**Query constructed by `TreasuryExchangeRateService`:**

```
GET {baseUrl}
  ?fields=country_currency_desc,exchange_rate,record_date
  &filter=country_currency_desc:eq:{currency},
          record_date:lte:{purchaseDate},
          record_date:gte:{purchaseDate minus 6 months}
  &sort=-record_date
  &page[size]=1
```

**Example for `Canada-Dollar`, purchase date `2024-06-15`:**
```
https://api.fiscaldata.treasury.gov/services/api/v1/accounting/od/rates_of_exchange
  ?fields=country_currency_desc,exchange_rate,record_date
  &filter=country_currency_desc:eq:Canada-Dollar,record_date:lte:2024-06-15,record_date:gte:2023-12-15
  &sort=-record_date
  &page[size]=1
```

**Expected response shape:**
```json
{
  "data": [
    {
      "country_currency_desc": "Canada-Dollar",
      "exchange_rate": "1.3500",
      "record_date": "2024-06-01"
    }
  ]
}
```

**Failure scenarios handled:**

| Scenario | Action |
|---|---|
| `data` is empty | Throw `ExchangeRateUnavailableException` → HTTP 422 |
| `data` is null | Throw `ExchangeRateUnavailableException` → HTTP 422 |
| `RestClientException` (timeout, DNS, 5xx) | Throw `ExchangeRateUnavailableException` → HTTP 422 |

**Timeouts (configurable):**

| Property | Default |
|---|---|
| `treasury.api.connect-timeout-ms` | 5000 ms |
| `treasury.api.read-timeout-ms` | 10000 ms |

---

## 10. Validation Rules

### `CreateTransactionRequest`

| Field | Annotation | Rule | HTTP on failure |
|---|---|---|---|
| `description` | `@NotBlank` | Must not be null or blank | 400 |
| `description` | `@Size(max=50)` | Max 50 characters | 400 |
| `transactionDate` | `@NotNull` | Must be present | 400 |
| `transactionDate` | *(Jackson deserialization)* | Must be valid ISO date `YYYY-MM-DD` | 400 |
| `purchaseAmountCents` | `@NotNull` | Must be present | 400 |
| `purchaseAmountCents` | `@Min(0)` | Must not be negative | 400 |

### Path / Query Parameters

| Parameter | Validation | HTTP on failure |
|---|---|---|
| `id` (path) | Must be a valid UUID format | 400 (Spring auto-handles) |
| `currency` (query) | Passed as-is to Treasury API; unknown values yield 422 from exchange rate lookup | 422 |

---

## 11. Error Handling

All errors return a consistent JSON structure:

```json
{
  "timestamp": "2024-06-15T10:30:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Purchase transaction not found for id: a1b2c3d4-..."
}
```

### HTTP Status Codes

| Status | Scenario | Exception |
|---|---|---|
| `400 Bad Request` | Validation failure on request body or path/query params | `MethodArgumentNotValidException`, `HttpMessageNotReadableException` |
| `404 Not Found` | Transaction UUID does not exist | `TransactionNotFoundException` |
| `422 Unprocessable Entity` | No exchange rate within 6 months, or Treasury API unavailable | `ExchangeRateUnavailableException` |
| `500 Internal Server Error` | Unexpected runtime error | `Exception` (catch-all) |

### `GlobalExceptionHandler`

Implemented as `@RestControllerAdvice`. Handles:
- `TransactionNotFoundException` → 404
- `ExchangeRateUnavailableException` → 422
- `MethodArgumentNotValidException` → 400 (collects all field error messages)
- `HttpMessageNotReadableException` → 400 (malformed JSON, bad date format)
- `Exception` → 500 (catch-all, hides internals)

---

## 12. Testing Strategy

### Test Pyramid

```
        ┌─────────────┐
        │ Integration │   4 tests  — Full Spring context + WireMock
        │    Tests    │
        └──────┬──────┘
        ┌──────▼──────┐
        │  Controller │   8 tests  — @WebMvcTest + MockMvc
        │    Tests    │
        └──────┬──────┘
        ┌──────▼──────┐
        │    Unit     │  12 tests  — Mockito, no Spring context
        │    Tests    │
        └─────────────┘
```

### Unit Tests

**`PurchaseTransactionServiceTest`**

| Test | Covers |
|---|---|
| `createTransaction_validRequest_returnsSavedTransaction` | Happy path store |
| `createTransaction_convertsCentsToDollars` | 9999 → 99.99 conversion |
| `createTransaction_oneCent_convertsToOneCentDollar` | Edge case: 1 cent → $0.01 |
| `getTransactionInCurrency_validRequest_returnsConvertedAmount` | Happy path retrieve + convert |
| `getTransactionInCurrency_roundsConvertedAmount` | 12.345 → 12.35 rounding |
| `getTransactionInCurrency_unknownId_throwsNotFoundException` | 404 path |
| `getTransactionInCurrency_noRateAvailable_throwsExchangeRateException` | 422 path |

**`TreasuryExchangeRateServiceTest`**

| Test | Covers |
|---|---|
| `getExchangeRate_validResponse_returnsRate` | Happy path |
| `getExchangeRate_emptyData_throwsExchangeRateUnavailable` | Empty API result |
| `getExchangeRate_nullResponse_throwsExchangeRateUnavailable` | Null API result |
| `getExchangeRate_restClientException_throwsExchangeRateUnavailable` | Network error |

### Controller Tests (`@WebMvcTest`)

| Test | Covers |
|---|---|
| `createTransaction_validBody_returns201` | Happy path POST |
| `createTransaction_blankDescription_returns400` | Blank description |
| `createTransaction_descriptionTooLong_returns400` | 51+ char description |
| `createTransaction_zeroAmount_returns201` | $0.00 free transaction |
| `createTransaction_negativeAmount_returns400` | Negative cents |
| `createTransaction_missingDate_returns400` | Missing date field |
| `getTransaction_validRequest_returns200WithConversion` | Happy path GET |
| `getTransaction_unknownId_returns404` | Unknown UUID |
| `getTransaction_noExchangeRate_returns422` | No rate available |

### Integration Tests (`@SpringBootTest` + WireMock)

| Test | Covers |
|---|---|
| `storeThenRetrieveWithConversion_returnsConvertedAmount` | Full end-to-end flow |
| `storeTransaction_oneCent_storedAsOneCentDollar` | 1 cent storage |
| `storeTransaction_descriptionTooLong_returns400` | Validation in full context |
| `storeTransaction_zeroAmountCents_returns201` | $0.00 in full context |
| `storeTransaction_negativeAmountCents_returns400` | Negative in full context |
| `storeTransaction_invalidDateFormat_returns400` | Bad date format |
| `retrieveTransaction_noRateWithinSixMonths_returns422` | Empty Treasury stub |
| `retrieveTransaction_unknownId_returns404` | Missing transaction |
| `retrieveTransaction_convertedAmountRoundedToTwoDecimals` | Rounding in full context |

### Running Tests

```bash
mvn test          # all tests
mvn verify        # tests + build verification
```

---

## 13. Configuration

### `application.properties`

```properties
# Server
server.port=8080

# Datasource (H2 — swap for PostgreSQL in production)
spring.datasource.url=jdbc:h2:mem:purchasedb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=update

# H2 console (disable in production)
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# Treasury API
treasury.api.base-url=https://api.fiscaldata.treasury.gov/services/api/v1/accounting/od/rates_of_exchange
treasury.api.connect-timeout-ms=5000
treasury.api.read-timeout-ms=10000

# Jackson
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.default-property-inclusion=non_null
```

### Production Overrides (example for PostgreSQL)

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/purchasedb
spring.datasource.username=wex_user
spring.datasource.password=secret
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.h2.console.enabled=false
```

---

## 14. Local Setup

### Prerequisites

- Java 21+
- Maven 3.8+

### Steps

```bash
# 1. Unzip or clone the project
cd wex-purchase-api

# 2. Build and run all tests
mvn clean verify

# 3. Start the server
mvn spring-boot:run

# Server starts at http://localhost:8080
# H2 console at http://localhost:8080/h2-console
#   JDBC URL: jdbc:h2:mem:purchasedb  (no password)
```

### Quick Smoke Test

```bash
# Store a transaction ($99.99)
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Office supplies",
    "transactionDate": "2024-06-15",
    "purchaseAmountCents": 9999
  }' | jq

# Retrieve with currency conversion (replace {id} with UUID from above)
curl -s "http://localhost:8080/api/v1/transactions/{id}?currency=Canada-Dollar" | jq

# Store a free ($0.00) transaction
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Free item",
    "transactionDate": "2024-06-15",
    "purchaseAmountCents": 0
  }' | jq
```

---

## 15. Design Decisions & Trade-offs

### Cents as Input Format

Accepting `purchaseAmountCents` as a `Long` integer avoids floating-point precision issues at the API boundary. The conversion to `BigDecimal` with `scale=2` and `HALF_UP` rounding is done exactly once, inside the service, before persistence. All subsequent arithmetic (currency conversion) uses `BigDecimal` throughout.

### H2 In-Memory Database

H2 was chosen for zero-configuration local development and test isolation. The datasource is fully externalised via `application.properties`, so swapping to PostgreSQL or any other JPA-supported database requires only a config change — no code changes.

### UUID Primary Key

UUIDs are generated in Java (`UUID.randomUUID()`) rather than delegated to the database. This makes the ID available immediately after object creation (useful in tests), avoids coupling to DB-specific auto-increment behaviour, and works consistently across any JPA-supported database.

### Treasury API: Page Size 1 + Server-Side Sort

Rather than fetching all rates and filtering in memory, the query pushes the date window filtering and `DESC` sort to the Treasury API and requests only 1 record. This minimises payload size and network overhead, and means the service does not need to implement any in-memory sorting or filtering logic.

### 422 for Exchange Rate Unavailability

HTTP `422 Unprocessable Entity` was chosen over `404` for missing exchange rates because the transaction itself exists — the request is semantically valid but cannot be fulfilled due to a data constraint (no rate in the 6-month window). `404` would imply the resource wasn't found, which would be misleading.

### No Caching of Exchange Rates

Exchange rates are fetched live per request. For a production system under load, a short-lived cache (e.g. Caffeine with a 1-hour TTL) would reduce Treasury API calls significantly. This is a straightforward addition and was omitted here to keep scope focused on the core requirements.
