# NovaMall

NovaMall is a Spring Cloud Alibaba based e-commerce microservice platform.
The first milestone focuses on a complete order flow before adding high-concurrency features.

## Requirements

- JDK 17 (IntelliJ IDEA OpenJDK 17 is supported)
- Maven 3.9+
- Docker Desktop with Compose v2+

## Modules

```text
mall-gateway       API entry point, port 8080
mall-auth          authentication, port 8081
mall-user          user domain, port 8082
mall-product       product domain, port 8083
mall-inventory     inventory domain, port 8084
mall-order         order domain, port 8085
mall-common        shared libraries
```

## Start infrastructure

```powershell
docker compose up -d
```

Infrastructure endpoints:

```text
Nacos:       http://localhost:8848/nacos
RabbitMQ:    http://localhost:15672
MinIO:       http://localhost:9001
MySQL:       localhost:3307 (宿主机端口；容器内仍为 3306)
Redis:       localhost:6379
Prometheus:  http://localhost:9090
Grafana:     http://localhost:3000
```

## Build

Use JDK 17 as the IntelliJ Maven Runner JRE, then run:

```powershell
mvn clean verify
```

The current automated tests include shared ID/JWT/trace utilities plus Mockito
unit tests for inventory reservation, seckill compensation, order lifecycle,
product cache behavior, and Elasticsearch fallback. Run only the tests with:

```powershell
mvn test
```

The database optimistic-lock inventory check can be run against the already
started inventory service. It creates one uniquely named test inventory row,
then sends concurrent reservations and verifies that stock is never oversold:

```powershell
./scripts/inventory-concurrency-test.ps1
```

## CI

The repository includes a GitHub Actions workflow at
`.github/workflows/ci.yml`. Every push to `main`/`master` and every pull
request runs the complete JDK 17 Maven build and test suite.

## Monitoring

Each application exposes Micrometer metrics at `/actuator/prometheus`.
Prometheus and Grafana are included in the local Compose stack. Prometheus
scrapes the six host-running applications through `host.docker.internal`.
Grafana is available at `http://localhost:3000` after starting the monitoring
containers; configure its Prometheus data source as
`http://host.docker.internal:9090`.

Services default to `localhost:3307`, matching the Compose MySQL host port.
No MySQL environment variables are needed for this local setup. Set
`MYSQL_HOST` and `MYSQL_PORT` explicitly when using another database endpoint;
applications running inside the Compose network must use `mysql:3306`.

## Run from IntelliJ IDEA

Open the root `pom.xml` as the Maven project and use JDK 17 for both the Project
SDK and Maven Runner JRE. Start the applications in this order:

```text
MallUserApplication        no extra variables
MallProductApplication     no extra variables
MallInventoryApplication   no extra variables
MallAuthApplication        no extra variables
MallOrderApplication       no extra variables
MallGatewayApplication     no extra variables
```

The user service uses its default port `8082`. Nacos publishes the actual port,
so Gateway and Feign do not require route changes.

## Current milestone

The user, authentication, product, and category flows are implemented. Product
detail reads use Redis Cache Aside; product updates and logical deletes evict
the detail cache. Product writes use MySQL through MyBatis-Plus.

Product endpoints:

```text
POST   /api/categories
GET    /api/categories
PUT    /api/categories/{id}
DELETE /api/categories/{id}
POST   /api/products
GET    /api/products/{id}
GET    /api/products?page=1&size=20&categoryId=&status=&keyword=
PUT    /api/products/{id}
DELETE /api/products/{id}
```

The order service obtains product snapshots from `/internal/products/{id}`;
internal endpoints are service-to-service endpoints and are not gateway routes.

Inventory and order endpoints:

```text
POST /api/inventory
GET  /api/inventory/{productId}
POST /api/orders
GET  /api/orders/{orderNo}
POST /api/orders/{orderNo}/cancel
POST /api/orders/{orderNo}/pay
POST /api/orders/{orderNo}/ship
POST /api/orders/{orderNo}/complete
```

Seckill endpoints:

```text
POST /api/seckill/{productId}/prepare
POST /api/seckill/{productId}
GET  /api/seckill/orders/{seckillNo}
```

The seckill path uses a Redis Lua script for atomic stock deduction and
per-user deduplication. Accepted requests are published to RabbitMQ and the
inventory service uses the existing optimistic-lock reservation path before
writing `mall_seckill_order`.

Product ranking endpoint:

```text
GET /api/products/hot?limit=10
GET /api/products/sales?limit=10
```

Each successful product detail read increments `product:hot` in Redis. The hot
endpoint returns active products ordered by their ZSet score.

When an order reaches `COMPLETED`, the order outbox includes product quantities.
The product service consumes `ORDER_COMPLETED` and increments the
`product:sales` Redis ZSet idempotently. The sales endpoint returns active
products ordered by completed sales.

Product search endpoint:

```text
GET /api/products/search?keyword=phone&categoryId=&minPrice=&maxPrice=&page=1&size=20
```

The search implementation uses Elasticsearch first and supports keyword,
category, price range, and pagination. Product writes synchronize the
`mall_products` index; MySQL remains the fallback when Elasticsearch is
unavailable.

The product service consumes completed-order events from RabbitMQ using the
local credentials `novamall/novamall` by default.

The gateway applies Redis Lua rate limiting by user ID after authentication or
by client IP for public endpoints. Default route limits are 5 requests per
second for authentication, 20 for product APIs, 5 for orders, and 2 for
seckill, with burst capacity configured in the gateway YAML.

After restarting `MallGatewayApplication`, verify the limiter with:

```powershell
./scripts/rate-limit-test.ps1
```

Order creation writes an `ORDER_CREATED` event to the local outbox in the same
transaction as the order. Manual or timeout cancellation writes an
`ORDER_CANCELLED` event. The order service publishes pending outbox messages to
RabbitMQ with publisher confirms and retry backoff; the inventory service
consumes cancellation events and releases reservations idempotently.

If the Compose MySQL volume was already created before `005-order-outbox.sql`
was added, run that script once manually against the `mall` database. Compose
only runs `/docker-entrypoint-initdb.d` scripts when the database is initialized
for the first time.

## Smoke checks

After starting the services, the following endpoints should return JSON:

```text
http://localhost:8080/actuator/health
http://localhost:8080/api/auth/ping
http://localhost:8080/api/users/ping
http://localhost:8080/api/products/ping
http://localhost:8080/api/inventory/ping
http://localhost:8080/api/orders/ping
```

Run the complete registration-to-order-cancellation check from PowerShell:

```powershell
./scripts/smoke-test.ps1
```

The local payment transition can be checked with:

```powershell
./scripts/payment-test.ps1
```

The local mock payment endpoint is enabled by default. Set
`MOCK_PAYMENT_ENABLED=false` when starting the order service to disable it.

The local fulfillment endpoints are enabled by default for development:

```powershell
./scripts/order-lifecycle-test.ps1
```

Set `MOCK_FULFILLMENT_ENABLED=false` to disable them.

## API documentation

Each application exposes an OpenAPI document and Swagger UI for local
development. After rebuilding and restarting the applications, verify all six
documents with:

```powershell
./scripts/openapi-test.ps1
```

Swagger UI endpoints:

```text
Gateway:   http://localhost:8080/swagger-ui.html
Auth:      http://localhost:8081/swagger-ui.html
User:      http://localhost:8082/swagger-ui.html
Product:   http://localhost:8083/swagger-ui.html
Inventory: http://localhost:8084/swagger-ui.html
Order:     http://localhost:8085/swagger-ui.html
```

The raw OpenAPI document is available at `/v3/api-docs` on each port. The
Gateway allows its documentation paths without a JWT so the local UI can be
opened directly.

After restarting `MallInventoryApplication` and `MallGatewayApplication`, run
the Redis Lua and RabbitMQ seckill check:

```powershell
./scripts/seckill-test.ps1
```

After restarting `MallProductApplication`, run the product-ranking check:

```powershell
./scripts/ranking-test.ps1
```

After restarting `MallProductApplication`, run the product-search check:

```powershell
./scripts/search-test.ps1
```

After restarting `MallProductApplication`, `MallOrderApplication`,
`MallInventoryApplication`, and `MallGatewayApplication`, run the completed
order sales-ranking check:

```powershell
./scripts/sales-ranking-test.ps1
```

## Trace ID

Gateway and all servlet services accept, propagate, and return `X-Trace-Id`.
If the request does not provide a valid value, Gateway generates a 32-character
lowercase hexadecimal ID. The servlet services put the ID into MDC, so service
logs include it as `traceId`. Synchronous OpenFeign calls from auth and order
services propagate the same ID to their downstream services.

After rebuilding, restart `MallGatewayApplication`, `MallAuthApplication`,
`MallUserApplication`, `MallProductApplication`, `MallInventoryApplication`,
and `MallOrderApplication`, then verify propagation and generation with:

```powershell
./scripts/trace-id-test.ps1
```
