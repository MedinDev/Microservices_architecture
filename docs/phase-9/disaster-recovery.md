# Phase 9.2 Disaster Recovery

## Backup Strategies

- Database backup automation:
  - `scripts/disaster-recovery/backup-postgres.sh`
  - Produces compressed dumps for `order_db`, `payment_db`, `notification_db`
- Configuration backup:
  - `scripts/disaster-recovery/backup-config.sh`
  - Archives `config/`, compose files, and root `pom.xml`

## Restore and Recovery

- Database restore:
  - `scripts/disaster-recovery/restore-postgres.sh <service> <backup_file.sql.gz>`
- Failover drill automation:
  - `scripts/disaster-recovery/drill-failover.sh [service-name]`
- Health validation during drills:
  - Gateway and all three business service actuator health checks

## Disaster Recovery Drills

- Drill frequency: weekly
- Drill sequence:
  1. Run backup scripts and validate files are generated
  2. Stop one critical component and validate degraded operation
  3. Restore component and verify healthy state
  4. Validate event flow by placing order and reading notifications

## High Availability Setup

- Service registry deployed with 2 replicas
- API gateway and business services deployed with at least 2 replicas
- HPA configured for gateway and business services
- PDB configured for gateway to preserve minimum availability during disruptions

## Multi-Region Deployment Guidance

- Active-passive regions with asynchronous replication for PostgreSQL
- Kafka MirrorMaker 2 for cross-region topic replication
- DNS or global load balancer based traffic failover
- Region-scoped secrets and independent observability stacks

## Failover Automation and Data Replication

- Kubernetes HPA and self-healing probe policies handle pod failover
- Managed PostgreSQL with PITR and read replica promotion for DB failover
- Kafka replicated topics plus replay strategy from DLT when required
- Redis configured as managed HA (sentinel or managed primary/replica)
