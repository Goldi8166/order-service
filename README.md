# Currency Order Service

A Quarkus microservice that accepts a currency conversion order, converts USD to a
target currency using a live exchange rate API, persists the order in PostgreSQL,
publishes an `ORDER_CREATED` event to Kafka, and indexes it into OpenSearch for
dashboarding.

## Architecture

```
Client -> Quarkus REST (POST /api/v1/orders)
            -> REST Client Reactive -> open.er-api.com (live rate)
            -> PostgreSQL (order persisted)
            -> Response returned to client
            -> Kafka topic "order-created-events" (async publish)
                 -> Kafka consumer (@Incoming) in same app
                      -> OpenSearch index "orders"
                           -> OpenSearch Dashboards (visualizations)
```

Order creation is synchronous (DB write + response). Kafka -> OpenSearch indexing
is asynchronous — if OpenSearch is temporarily unavailable, order creation still
succeeds; only the dashboard update is delayed/dropped for that event.

## Prerequisites

- JDK 17+
- Maven 3.9+ (or use `./mvnw` if you add the wrapper)
- Docker + Docker Compose

## Running everything with Docker Compose (recommended)

```bash
docker-compose up -d --build
```

This starts PostgreSQL, Kafka, OpenSearch, OpenSearch Dashboards, and the
order-service itself, all wired together. First build takes a few minutes.

Check it's up:
```bash
docker-compose ps
curl http://localhost:8080/q/health
```

## Running locally in dev mode (without containerizing the app)

Start just the infra:
```bash
docker-compose up -d postgres kafka opensearch opensearch-dashboards
```

Then run the app locally (uses localhost defaults from application.properties):
```bash
mvn quarkus:dev
```

Dev mode gives you live reload on code changes.

## Sample curl calls

Create an order:
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-1001","amountUSD":150.00,"targetCurrency":"EUR"}'
```

Expected response:
```json
{
  "orderId": 1,
  "customerId": "CUST-1001",
  "amountUSD": 150.00,
  "targetCurrency": "EUR",
  "convertedAmount": 138.50,
  "status": "PROCESSED",
  "createdAt": "2026-09-03T10:00:00Z"
}
```

Invalid currency (returns 400):
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-1001","amountUSD":100,"targetCurrency":"ZZZ"}'
```

Swagger UI: http://localhost:8080/q/swagger-ui

## Running tests

Unit tests only (mocks the exchange rate client, needs Postgres running via
docker-compose but not Kafka/OpenSearch — Kafka is swapped for an in-memory
connector automatically in the test profile):
```bash
docker-compose up -d postgres
mvn test
```

Full integration test suite (REST Assured, hits the real running endpoint):
```bash
mvn verify
```

## OpenSearch Dashboard setup

1. Create at least one order via curl (above) so there's data to visualize.
2. Open OpenSearch Dashboards: http://localhost:5601
3. Go to **Stack Management -> Index Patterns** -> create pattern `orders*`,
   timestamp field = `createdAt`.
4. Go to **Visualize -> Create Visualization** and create each of the following:

| Visualization | Config |
|---|---|
| Metric | Aggregation: Sum, Field: `amountUSD` |
| Line Chart | X-axis: Date Histogram on `createdAt`; Y-axis: Sum of `amountUSD` |
| Donut/Pie | Split slices: Terms aggregation on `targetCurrency.keyword` |
| Data Table | Bucket: Terms on `customerId.keyword`; Metric: Sum of `amountUSD`; Order: Descending; Size: 5 |

5. Go to **Dashboard -> Create Dashboard**, add all four visualizations, save as
   "Order Revenue Dashboard".

> Note: `targetCurrency` and `customerId` must aggregate on their `.keyword`
> sub-field (not the analyzed `text` field) for Terms aggregations to group
> correctly.

## Error handling

| Scenario | HTTP Status |
|---|---|
| Missing/invalid request fields (bean validation) | 400 |
| Unsupported/invalid `targetCurrency` | 400 |
| Exchange rate API unreachable or returns failure | 502 |
| Unexpected server error | 500 |

## Project structure

```
src/main/java/com/example/
  model/       - Order entity, OrderStatus enum
  dto/         - OrderRequest, OrderResponse, ExchangeRateResponse
  client/      - ExchangeRateClient (REST Client Reactive)
  service/     - OrderService (business logic)
  resource/    - OrderResource (REST endpoint)
  messaging/   - OrderEventConsumer (Kafka -> OpenSearch), OpenSearchClientProducer
  exception/   - Custom exceptions + global exception mapper
```
