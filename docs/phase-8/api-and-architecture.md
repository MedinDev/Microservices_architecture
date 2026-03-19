# Phase 8.1 API and Architecture Documentation

## API Documentation

### OpenAPI and Swagger by service

- Order Service: `http://localhost:8081/swagger-ui.html` and `http://localhost:8081/v3/api-docs`
- Payment Service: `http://localhost:8082/swagger-ui.html` and `http://localhost:8082/v3/api-docs`
- Notification Service: `http://localhost:8083/swagger-ui.html` and `http://localhost:8083/v3/api-docs`

### API versioning strategy

- Versioning style: URI major versioning at gateway (`/api/v1/...`) with compatibility aliases (`/api/...`)
- Current supported major version: `v1`
- Backward compatibility:
  - Existing routes remain available under `/api/...`
  - New clients should use `/api/v1/...`
  - Gateway injects `X-Api-Version: 1` for downstream observability
- Deprecation policy:
  - Mark endpoints as deprecated in OpenAPI before removal
  - Keep one major version overlap window during migrations
- Routing policy:
  - Gateway routes both versioned and compatibility paths to service-native endpoints
  - Service controllers stay stable on internal paths (`/api/orders`, `/api/payments`, `/api/notifications`)

### Postman collection

- Collection file: `docs/phase-8/postman/microservices-platform.postman_collection.json`
- Variables:
  - `gatewayUrl` default `http://localhost:8080`
  - `bearerToken` for OAuth2 JWT
  - `orderId`, `paymentId`, `notificationId`, `userId`

## Architecture Documentation

### C4 context diagram

```mermaid
flowchart LR
    User[Customer / Backoffice User] --> Gateway[API Gateway]
    Gateway --> Order[Order Service]
    Gateway --> Payment[Payment Service]
    Gateway --> Notification[Notification Service]

    Order --> Kafka[(Kafka Cluster)]
    Payment --> Kafka
    Notification --> Kafka

    Order --> OrderDB[(Order PostgreSQL)]
    Payment --> PaymentDB[(Payment PostgreSQL)]
    Notification --> NotificationDB[(Notification PostgreSQL)]

    Gateway --> Redis[(Redis)]
    Order --> Redis
    Payment --> Redis

    Gateway --> Eureka[(Service Registry)]
    Order --> Eureka
    Payment --> Eureka
    Notification --> Eureka
```

### C4 container diagram

```mermaid
flowchart TB
    subgraph Platform[Microservices Platform]
        APIGW[Spring Cloud Gateway]
        ORD[Order Service]
        PAY[Payment Service]
        NOTI[Notification Service]
        EUREKA1[Eureka Peer 1]
        EUREKA2[Eureka Peer 2]
        REDIS[Redis]
        KAFKA1[Kafka Broker 1]
        KAFKA2[Kafka Broker 2]
    end

    APIGW --> ORD
    APIGW --> PAY
    APIGW --> NOTI
    ORD --> KAFKA1
    PAY --> KAFKA1
    NOTI --> KAFKA1
    ORD --> REDIS
    PAY --> REDIS
    APIGW --> REDIS
    APIGW --> EUREKA1
    APIGW --> EUREKA2
    ORD --> EUREKA1
    PAY --> EUREKA1
    NOTI --> EUREKA1
```

### Data flow diagram

```mermaid
flowchart LR
    C[Client] --> G[API Gateway]
    G --> O[Order API]
    O --> ODB[(Order DB)]
    O --> OB[Outbox Table]
    O --> K[(Kafka ORDER_EVENTS)]
    K --> P[Payment Consumer]
    P --> PDB[(Payment DB)]
    P --> K2[(Kafka PAYMENT_EVENTS)]
    K2 --> O2[Order Payment Consumer]
    K2 --> N[Notification Consumer]
    O --> K3[(Kafka INVENTORY/ORDER events)]
    N --> NDB[(Notification DB)]
```

### Sequence diagram: order creation to payment completion

```mermaid
sequenceDiagram
    participant U as User
    participant G as API Gateway
    participant O as Order Service
    participant K as Kafka
    participant P as Payment Service
    participant N as Notification Service

    U->>G: POST /api/v1/orders
    G->>O: POST /api/orders
    O->>O: Persist order + outbox event
    O->>K: Publish ORDER_CREATED
    K->>P: Consume ORDER_CREATED
    P->>P: Process payment
    P->>K: Publish PAYMENT_PROCESSED or PAYMENT_FAILED
    K->>O: Consume payment event
    O->>O: Update order status
    K->>N: Consume domain event
    N->>N: Persist and send notification
```

### Sequence diagram: cancellation compensation flow

```mermaid
sequenceDiagram
    participant U as Admin User
    participant G as API Gateway
    participant O as Order Service
    participant K as Kafka
    participant P as Payment Service
    participant N as Notification Service

    U->>G: PUT /api/v1/orders/{id}/cancel
    G->>O: PUT /api/orders/{id}/cancel
    O->>K: Publish ORDER_CANCELLED
    K->>P: Consume ORDER_CANCELLED
    P->>P: Compensate and refund if processed
    P->>K: Publish REFUND_PROCESSED
    K->>N: Consume cancellation/refund events
    N->>N: Send user notification
```
