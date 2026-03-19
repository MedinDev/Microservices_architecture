# Phase 8.1 Operational Documentation

## Deployment Guide

### Prerequisites

- Docker and Docker Compose
- Java 21 and Maven 3.9+ for local module builds

### Start the platform

```bash
docker compose up -d --build
```

### Validate platform health

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
curl -fsS http://localhost:8083/actuator/health
```

### Validate observability endpoints

```bash
curl -fsS http://localhost:8081/actuator/prometheus
curl -fsS http://localhost:8082/actuator/prometheus
curl -fsS http://localhost:8083/actuator/prometheus
```

## Runbooks for Common Issues

### Service not starting

1. Check container health and logs:
   - `docker compose ps`
   - `docker compose logs --tail=200 <service-name>`
2. Confirm dependency health:
   - Postgres for service
   - Kafka brokers
   - Service registry peers
3. Validate env variables:
   - `DB_*`
   - `KAFKA_BOOTSTRAP_SERVERS`
   - `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`

### Kafka consumer lag increases

1. Inspect topic and consumer lag in Kafka UI (`http://localhost:8085`)
2. Scale consumer concurrency via env:
   - `KAFKA_LISTENER_CONCURRENCY`
3. Tune poll and batching:
   - `KAFKA_CONSUMER_MAX_POLL_RECORDS`
   - `KAFKA_CONSUMER_FETCH_MIN_BYTES`
4. Verify broker resources and retention configuration

### High API latency

1. Check gateway rate limiting behavior and Redis health
2. Verify DB pool saturation:
   - `DB_POOL_MAX_SIZE`
   - `DB_POOL_CONNECTION_TIMEOUT_MS`
3. Validate service-level resilience metrics:
   - Circuit breaker open rates
   - Retry counts
4. Inspect query plan on hot queries and validate indexes

### Notification delivery delays

1. Validate Kafka consumption in notification service
2. Confirm mail server connectivity
3. Check notification table growth and cleanup schedule
4. Verify WebSocket broker session count and logs

## Recovery Procedures

### Postgres recovery

1. Stop impacted service to freeze writes:
   - `docker compose stop order-service` (or payment/notification)
2. Restore DB volume or logical backup for impacted database
3. Run Flyway migrations on startup
4. Start service and verify:
   - API health endpoint
   - DB connectivity
   - key business read APIs

### Kafka broker recovery

1. Recover one broker at a time to preserve cluster availability
2. Verify ISR and under-replicated partitions
3. Confirm topic configs:
   - partitions
   - replication factor
   - retention policy
4. Resume consumer monitoring and check lag convergence

### Service-level recovery after bad deploy

1. Roll back image tag or revert module build
2. Restart single service container
3. Validate:
   - health endpoint
   - critical API smoke tests
   - event production/consumption
4. Watch metrics and logs for at least one full business flow

### Event replay strategy

1. For idempotent consumers, replay from offsets after root cause fix
2. Use processed event tables to avoid double side effects
3. Replay DLT topics only after payload and code validation
