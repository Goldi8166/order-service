# Currency Order Service

A Quarkus-based microservice that accepts currency conversion orders, converts USD to a target currency using a live exchange-rate API, persists the order in PostgreSQL, publishes an `ORDER_CREATED` event to Kafka, and indexes the order into OpenSearch for analytics and dashboarding.

The service demonstrates a hybrid synchronous + asynchronous architecture:

- Synchronous: REST API → Exchange Rate API → PostgreSQL → HTTP response
- Asynchronous: Kafka → Consumer → OpenSearch → OpenSearch Dashboards

---

## Architecture

```mermaid
flowchart LR
    C[Client / Postman]
    R[Quarkus REST API<br/>POST /api/v1/orders]
    S[OrderService<br/>Business Logic]
    FX[Exchange Rate API<br/>open.er-api.com]
    DB[(PostgreSQL<br/>Orders)]
    K[Kafka<br/>order-created-events]
    CON[OrderEventConsumer]
    OS[(OpenSearch<br/>orders index)]
    D[OpenSearch Dashboards]

    C -->|POST Order| R
    R --> S
    S -->|Get live exchange rate| FX
    FX -->|Exchange rate| S
    S -->|Persist order| DB
    DB -->|Saved order| S
    S -->|Publish ORDER_CREATED| K
    K --> CON
    CON -->|Index document| OS
    OS --> D
    S -->|201 Created| R
    R --> C
Request Flow

When a client creates a currency order, the request follows this flow:

Client sends a POST /api/v1/orders request.
OrderResource receives the request.
OrderService handles the business logic.
ExchangeRateClient calls the live exchange-rate API.
The conversion is calculated using BigDecimal.
The order is persisted in PostgreSQL.
An ORDER_CREATED event is published to Kafka.
The REST API returns the order response to the client.
OrderEventConsumer consumes the Kafka event asynchronously.
The consumer indexes the order into OpenSearch.
OpenSearch Dashboards uses the indexed data for analytics and visualizations.

The REST request does not need to perform the OpenSearch indexing itself. This keeps the customer-facing transaction path separate from the analytics pipeline.

Technology Stack
Technology	Purpose
Java 17+	Application development
Quarkus	Microservice framework
REST API	Order creation endpoint
REST Client Reactive	Communication with exchange-rate API
PostgreSQL	Transactional order storage
Hibernate ORM / Panache	Database persistence
Apache Kafka	Event streaming
SmallRye Reactive Messaging	Kafka integration
OpenSearch	Search and analytics
OpenSearch Dashboards	Data visualization
Docker	Containerization
Docker Compose	Multi-container environment
JUnit 5	Testing
Mockito	Mocking dependencies
REST Assured	API integration testing
Maven	Build and dependency management
Features
Currency conversion order API
Live exchange-rate integration
PostgreSQL persistence
Kafka event publishing
Asynchronous Kafka consumer
OpenSearch indexing
OpenSearch Dashboards analytics
Input validation
Error handling
Docker Compose environment
Automated tests
Swagger / OpenAPI documentation
API
Create Currency Order
Endpoint
POST /api/v1/orders
Request
{
  "customerId": "CUST-1001",
  "amountUSD": 150.00,
  "targetCurrency": "EUR"
}
Example cURL
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-1001",
    "amountUSD": 150.00,
    "targetCurrency": "EUR"
  }'
Example Response
{
  "orderId": 1,
  "customerId": "CUST-1001",
  "amountUSD": 150.00,
  "targetCurrency": "EUR",
  "convertedAmount": 138.50,
  "status": "PROCESSED",
  "createdAt": "2026-09-04T05:18:06.203852Z"
}

convertedAmount depends on the live exchange rate returned by the external exchange-rate API.

API Documentation

Swagger UI is available when the application is running:

http://localhost:8080/q/swagger-ui

Quarkus also exposes the OpenAPI specification through:

http://localhost:8080/q/openapi
Error Handling

The API handles the following error scenarios:

Scenario	HTTP Status
Invalid request / validation failure	400 Bad Request
Unsupported currency	400 Bad Request
Exchange-rate API failure	502 Bad Gateway
Unexpected server error	500 Internal Server Error

Example:

Invalid currency: ZZZ
→ 400 Bad Request

The HTTP response status represents the API outcome, while the database status field represents the persisted order state.

Database

PostgreSQL is used as the transactional source of truth for currency orders.

Orders Table

The main order data contains:

Column	Description
id	Primary key
customer_id	Customer identifier
amount_usd	Original USD amount
target_currency	Target currency
converted_amount	Converted amount
status	Order processing status
created_at	Order creation timestamp

Example order:

ID:               1
Customer ID:      CUST-1001
Amount USD:       150.00
Target Currency:  EUR
Converted Amount: 138.50
Status:           PROCESSED
Created At:       2026-09-04T05:18:06Z
Why PostgreSQL and OpenSearch?

PostgreSQL and OpenSearch serve different purposes.

PostgreSQL

PostgreSQL is responsible for transactional and durable application data.

It provides:

ACID transactions
Strong consistency for transactional operations
Relational data modelling
Constraints and relationships
Reliable persistent storage
OpenSearch

OpenSearch is used for:

Fast search
Aggregations
Analytics
Dashboard visualizations
Read-optimized analytical queries

The same order can therefore exist in both systems, but each system has a different responsibility.

PostgreSQL
    ↓
Source of truth

OpenSearch
    ↓
Search + Analytics + Dashboard
Kafka

Kafka is used to decouple order creation from the analytics/indexing pipeline.

Topic
order-created-events

When an order is successfully processed, an ORDER_CREATED event is published.

The Kafka flow is:

OrderService
     |
     | ORDER_CREATED
     ↓
Kafka Topic
order-created-events
     |
     ↓
OrderEventConsumer
     |
     ↓
OpenSearch
Producer

OrderService publishes the order event to Kafka.

Consumer

OrderEventConsumer consumes the event using Quarkus Reactive Messaging.

Conceptually:

@Incoming("order-events-in")

The consumer processes the event and indexes it into OpenSearch.

Consumer Group
order-service-indexer

The consumer group allows Kafka to track consumption progress and provides the foundation for scaling consumers.

Why Kafka?

Kafka provides an asynchronous event-driven boundary between the transactional application flow and the analytics pipeline.

Without Kafka:

REST Request
    ↓
PostgreSQL
    ↓
OpenSearch
    ↓
Response

With Kafka:

REST Request
    ↓
PostgreSQL
    ↓
Kafka
    ↓
OpenSearch

This separation allows the analytics/indexing pipeline to evolve independently from the core order-processing flow.

Kafka also provides concepts such as:

Topics
Partitions
Consumer groups
Offsets
Message retention
Event replay
Asynchronous Processing

Order creation and analytics indexing are intentionally separated.

Synchronous path
Client
  ↓
REST API
  ↓
OrderService
  ↓
Exchange Rate API
  ↓
PostgreSQL
  ↓
HTTP Response
Asynchronous path
Kafka
  ↓
OrderEventConsumer
  ↓
OpenSearch
  ↓
Dashboard

This means OpenSearch indexing is eventually consistent with PostgreSQL.

There can be a short delay between an order being persisted in PostgreSQL and becoming visible in OpenSearch Dashboards.

Actual delivery, retry, and failure behavior depends on the Kafka and application messaging configuration.

OpenSearch

Orders are indexed into:

orders

Each OpenSearch document represents an order.

The orderId is used as the document identifier so that repeated processing of the same order can update the same document rather than unnecessarily creating duplicate documents.

Example document:

{
  "orderId": 1,
  "customerId": "CUST-1001",
  "amountUSD": 150.00,
  "targetCurrency": "EUR",
  "convertedAmount": 138.50,
  "status": "PROCESSED",
  "createdAt": "2026-09-04T05:18:06Z"
}
Keyword Fields

Fields such as:

targetCurrency.keyword
customerId.keyword

are used for Terms aggregations.

For example:

targetCurrency.keyword

can be used to calculate how many orders or how much revenue belongs to each currency.

Similarly:

customerId.keyword

can be used to aggregate order amounts by customer.

OpenSearch Dashboard

OpenSearch Dashboards provides an analytics view called:

Order Revenue Dashboard

The dashboard contains visualizations such as:

1. Total Order Revenue

Metric:

SUM(amountUSD)
2. Revenue Over Time
Date Histogram: createdAt
Metric: SUM(amountUSD)
3. Revenue by Currency

Terms aggregation:

targetCurrency.keyword

Displayed as a donut/pie visualization.

4. Top Customers

Terms aggregation:

customerId.keyword

Metric:

SUM(amountUSD)

Sorted by descending revenue.

Size:

5
Docker

The application can be run using Docker Compose.

The environment contains the following services:

PostgreSQL
Kafka
OpenSearch
OpenSearch Dashboards
Order Service

Start the complete environment:

docker compose up -d --build

Check running containers:

docker compose ps

Check application health:

curl http://localhost:8080/q/health
Docker Services
Service	Purpose	Port
Order Service	Quarkus application	8080
PostgreSQL	Database	5432
Kafka	Event streaming	9092 / 29092
OpenSearch	Search and analytics	9200
OpenSearch Dashboards	Visualization	5601
Local Development

If you want to run the Quarkus application directly from Maven while running infrastructure through Docker:

docker compose up -d postgres kafka opensearch opensearch-dashboards

Then start Quarkus in development mode:

mvn quarkus:dev

Or, if the Maven wrapper is available:

./mvnw quarkus:dev
Prerequisites

Make sure the following are installed:

JDK 17 or newer
Maven 3.9+
Docker
Docker Compose

Verify:

java -version
mvn -version
docker --version
docker compose version
Testing

The project contains business-logic tests and integration tests.

Business Logic Tests

The exchange-rate client can be mocked so that the business logic can be tested without calling the real external API.

Testing tools include:

JUnit 5
Mockito
Quarkus Test
Mocked exchange-rate client

Run tests:

docker compose up -d postgres
mvn test

Kafka can be replaced with an in-memory connector in the test profile where configured.

Integration Tests

Integration tests exercise the REST API using REST Assured and the application environment.

Run:

mvn verify
Project Structure
src/
└── main/
    └── java/
        └── com/example/
            ├── model/
            │   ├── Order
            │   └── OrderStatus
            │
            ├── dto/
            │   ├── OrderRequest
            │   ├── OrderResponse
            │   └── ExchangeRateResponse
            │
            ├── client/
            │   └── ExchangeRateClient
            │
            ├── service/
            │   └── OrderService
            │
            ├── resource/
            │   └── OrderResource
            │
            ├── messaging/
            │   ├── OrderEventConsumer
            │   └── OpenSearchClientProducer
            │
            └── exception/
                ├── Custom Exceptions
                └── Global Exception Mapper
Main Components
OrderResource

Responsible for exposing the REST endpoint.

POST /api/v1/orders

It receives the request and delegates business processing to OrderService.

OrderService

Contains the main business logic:

Validate order
Fetch exchange rate
Calculate converted amount
Persist order
Publish Kafka event
Return order response
ExchangeRateClient

Communicates with the external exchange-rate service.

The project uses:

open.er-api.com

to obtain live exchange-rate information.

OrderEventConsumer

Consumes Kafka events and forwards the order data to OpenSearch.

Kafka
  ↓
OrderEventConsumer
  ↓
OpenSearch
PostgreSQL

Stores the durable transactional representation of orders.

OpenSearch

Stores documents optimized for search and analytics.

Currency Calculation

Currency calculations use BigDecimal instead of floating-point arithmetic.

Conceptually:

convertedAmount = amountUSD × exchangeRate

Using BigDecimal helps avoid common floating-point precision issues that are especially important in financial calculations.

Architecture Characteristics

This project demonstrates several important backend architecture concepts:

Microservice Architecture

The application is independently deployable and exposes functionality through a REST API.

Synchronous Processing

The order request requires the exchange-rate lookup and database persistence before returning the API response.

Asynchronous Processing

Kafka decouples order events from the OpenSearch indexing pipeline.

Event-Driven Architecture

The ORDER_CREATED event represents a business event that can be consumed by downstream components.

Eventual Consistency

PostgreSQL and OpenSearch may temporarily differ because OpenSearch is updated asynchronously.

Separation of Concerns

Different components have different responsibilities:

REST Layer
    ↓
Business Layer
    ↓
Persistence / External API / Messaging
Running the Complete Application
1. Start the complete environment
docker compose up -d --build
2. Check services
docker compose ps
3. Check application health
curl http://localhost:8080/q/health
4. Create an order
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-1001",
    "amountUSD": 150.00,
    "targetCurrency": "EUR"
  }'
5. Open OpenSearch Dashboards
http://localhost:5601
6. Create Index Pattern

Use:

orders*

Select:

createdAt

as the timestamp field.

7. Open the Order Revenue Dashboard

The dashboard can then be used to analyze:

Total order revenue
Revenue over time
Revenue by currency
Top customers
Health Check

The application exposes Quarkus health endpoints.

Check:

curl http://localhost:8080/q/health

A successful response indicates that the application health endpoint is available.

Summary

Currency Order Service demonstrates a production-oriented backend architecture using Quarkus, PostgreSQL, Kafka, OpenSearch, and Docker.

The core flow is:

Client
  ↓
Quarkus REST API
  ↓
OrderService
  ├──→ Exchange Rate API
  │
  ├──→ PostgreSQL
  │
  └──→ Kafka
          ↓
     OrderEventConsumer
          ↓
      OpenSearch
          ↓
  OpenSearch Dashboards

The architecture combines transactional processing with event-driven analytics, providing a clear separation between the core order-processing path and downstream analytical processing.
