# WEX Purchase Transaction API

A production-ready REST API to store purchase transactions in USD and retrieve them converted to any currency supported by the [US Treasury Reporting Rates of Exchange](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange).

📄 **[Technical Specification](./TECH_SPEC.md)** — detailed design, architecture, API contracts, business logic, and testing strategy.

---

## Requirements

- Java 21+
- Maven 3.8+

📄 **[Technical Specification](./TECH_SPEC.md)** — detailed design, architecture, API contracts, business logic, and testing strategy.

---

## Quick Start

```bash
# Clone and navigate
cd wex-purchase-api

# Build and run tests
mvn clean verify

# Start the server
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

An H2 console is available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:purchasedb`, no password).

📄 **[Technical Specification](./TECH_SPEC.md)** — detailed design, architecture, API contracts, business logic, and testing strategy.

---

## API Reference

### 1. Store a Purchase Transaction

**`POST /api/v1/transactions`**

Request body:
```json
{
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmount": 99.99
}
```

| Field             | Type       | Rules                                    |
|-------------------|------------|------------------------------------------|
| `description`     | `string`   | Required, max 50 characters              |
| `transactionDate` | `date`     | Required, ISO format `YYYY-MM-DD`        |
| `purchaseAmount`  | `number`   | Required, positive, rounded to 2 decimals|

Response `201 Created`:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 99.99
}
```

📄 **[Technical Specification](./TECH_SPEC.md)** — detailed design, architecture, API contracts, business logic, and testing strategy.

---

### 2. Retrieve a Transaction in a Target Currency

**`GET /api/v1/transactions/{id}?currency={countryCurrencyDesc}`**

| Parameter  | Description                                                         |
|------------|---------------------------------------------------------------------|
| `id`       | UUID returned when the transaction was created                      |
| `currency` | Country-currency label from Treasury API, e.g. `Canada-Dollar`     |

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

**Currency label examples** (from Treasury API `country_currency_desc` field):

| Country / Region | Label               |
|------------------|---------------------|
| Canada           | `Canada-Dollar`     |
| Euro Zone        | `Euro Zone-Euro`    |
| Japan            | `Japan-Yen`         |
| United Kingdom   | `United Kingdom-Pound` |
| Australia        | `Australia-Dollar`  |

For the full list, query the Treasury API directly:
```
https://api.fiscaldata.treasury.gov/services/api/v1/accounting/od/rates_of_exchange?fields=country_currency_desc&page[size]=200
```

📄 **[Technical Specification](./TECH_SPEC.md)** — detailed design, architecture, API contracts, business logic, and testing strategy.

---

## Error Responses

All errors return a structured JSON body:

```json
{
  "timestamp": "2024-06-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Purchase transaction not found for id: ..."
}
```

| HTTP Status | Scenario                                                        |
|-------------|-----------------------------------------------------------------|
| `400`       | Validation failure (blank description, bad date, negative amount) |
| `404`       | Transaction ID not found                                        |
| `422`       | No exchange rate available within 6 months of purchase date     |
| `500`       | Unexpected server error                                         |

📄 **[Technical Specification](./TECH_SPEC.md)** — detailed design, architecture, API contracts, business logic, and testing strategy.

---

## Exchange Rate Logic

- The Treasury API is queried for rates with `record_date` between `purchaseDate - 6 months` and `purchaseDate`.
- The most recent rate in that window is used (does not require an exact date match).
- If no rate is found, `422 Unprocessable Entity` is returned.
- Converted amounts are rounded to two decimal places (HALF_UP).

📄 **[Technical Specification](./TECH_SPEC.md)** — detailed design, architecture, API contracts, business logic, and testing strategy.

---

## Production Notes

- **Database**: H2 in-memory is used by default. For production, add a PostgreSQL (or similar) datasource in `application.properties` and update `spring.jpa.database-platform` accordingly.
- **Timeouts**: Treasury API connect/read timeouts are configurable via `treasury.api.connect-timeout-ms` and `treasury.api.read-timeout-ms`.
- **H2 Console**: Disable in production by setting `spring.h2.console.enabled=false`.

📄 **[Technical Specification](./TECH_SPEC.md)** — detailed design, architecture, API contracts, business logic, and testing strategy.

---

## Running Tests

```bash
# All tests (unit + integration)
mvn test

# With coverage report (target/site/jacoco/index.html)
mvn verify
```

Test layers:
- **Unit** – `PurchaseTransactionServiceTest`, `TreasuryExchangeRateServiceTest` (Mockito)
- **Controller** – `PurchaseTransactionControllerTest` (MockMvc, `@WebMvcTest`)
- **Integration** – `PurchaseTransactionIntegrationTest` (full Spring context + WireMock)
