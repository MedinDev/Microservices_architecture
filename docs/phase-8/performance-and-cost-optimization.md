# Phase 8.2 and 8.3 Performance and Cost Optimization

## Database optimization

- Query optimization:
  - Converted order item relation loading to lazy fetch in `OrderEntity`
  - Added explicit entity graph fetch on order read methods in `OrderRepository`
- Index analysis and improvements:
  - Added retention and read-path indexes in V2 Flyway migrations for all service databases
  - Optimized outbox scan and cleanup indexes in order service schema
  - Added payment log, refund, and processed-event cleanup indexes
  - Added notification read and processed-event cleanup indexes
- Connection pooling tuning:
  - Hikari configured for all services with tuned max pool size, idle, lifetime, timeout, leak detection
  - Exposed all pool settings via environment variables for runtime adjustments

## Kafka optimization

- Partition strategy:
  - Topic partition and replication factor externalized and applied through common topic builder
  - Default increased to 6 partitions with replication factor 2 for better parallelism
- Consumer group tuning:
  - Listener concurrency configurable per service (`app.kafka.listener.concurrency`)
  - Consumer fetch and poll settings tuned in shared Kafka configuration
- Batch size optimization:
  - Producer batch size, linger, and compression configured centrally
  - Consumer ack mode switched to batch with explicit poll timeout
- Retention controls:
  - Domain topics and DLT topics now carry explicit retention configuration

## Service optimization

- Response caching:
  - Payment read caches converted to bounded LRU maps to prevent unbounded growth
  - Gateway route-level rate limits retained to smooth downstream bursts
- Lazy loading strategies:
  - Order items switched to lazy relation loading
  - Entity graph added on read queries that need item hydration
- Memory leak analysis and prevention:
  - Removed unbounded in-memory read caches in payment service
  - Added scheduled historical cleanup jobs in order, payment, and notification services
  - Added retention-driven repository delete operations for event logs and old records

## Cost optimization

### Resource optimization and rightsizing

- Added CPU and memory limits for stateful and stateless containers in `docker-compose.yml`
- Sized services by workload profile:
  - Gateway and core services balanced for steady throughput
  - Kafka and Postgres assigned higher limits where required

### Auto-scaling policies

- Horizontal scaling policy for production orchestration:
  - Scale by CPU utilization, consumer lag, and p95 latency
  - Scale payment and order consumers with lag-driven thresholds
  - Keep minimum of 2 instances for gateway and critical domain services
- Compose environment keeps static right-sized defaults, while policy targets are documented for orchestrators

### Spot instance usage strategy

- Recommended placement:
  - Stateless services and asynchronous workers eligible for spot pools
  - Stateful services (Kafka, Postgres, Redis) remain on on-demand nodes
- Guardrails:
  - Pod disruption budgets for stateless services
  - Graceful shutdown hooks and idempotent event processing

### Data lifecycle management

- Data archiving strategy:
  - Archive historical payment transaction logs and read notifications before deletion windows
  - Keep refund records longer than operational event dedupe records
- Log rotation policies:
  - Container-level json-file rotation configured in compose
  - Service-level logback rolling policies configured per service
- Message retention periods:
  - Kafka retention set for primary topics and DLT topics through centralized topic config
