# Phase 9.3 Go-Live Preparation

## Monitoring Handover

- Dashboards available:
  - `config/grafana/dashboards/service-health.json`
  - `config/grafana/dashboards/business-kpi.json`
- Data sources provisioned from:
  - `config/grafana/provisioning/datasources/prometheus.yml`
- Monitoring stack compose:
  - `docker-compose.monitoring.yml`

## Dashboard Documentation and Alert Validation

- Validate dashboard panels:
  - API availability and latency
  - Kafka broker health and consumer lag
  - Order/payment/notification business counters
- Validate alert rules from:
  - `config/prometheus/alerts.yml`
- Alert routing configuration:
  - `config/alertmanager/alertmanager.yml`
- Alert threshold validation checklist:
  1. Trigger synthetic high latency and verify alert firing
  2. Stop one service and verify availability alerts
  3. Generate Kafka lag and verify lag alert trigger
  4. Confirm alert resolve notifications

## On-Call Rotation Setup

- Primary on-call: Platform Engineer
- Secondary on-call: Backend Engineer
- Incident commander: Engineering Manager
- Rotation schedule:
  - Weekday business hours: primary + secondary
  - Off-hours and weekends: primary, escalation to secondary in 15 minutes

## Runbooks and Incident Response

- Existing operational runbooks:
  - `docs/phase-8/operations.md`
- Incident response procedure:
  1. Detect and triage from monitoring alerts
  2. Classify severity and assign incident commander
  3. Contain impact with traffic shaping, rollback, or failover
  4. Recover service and validate critical business flows
  5. Publish incident summary and follow-up actions

## Escalation Matrix

- SEV-1:
  - Immediate page: primary + secondary + incident commander
  - Escalate to Head of Engineering within 10 minutes
- SEV-2:
  - Primary on-call first, secondary if unresolved after 20 minutes
- SEV-3:
  - Ticket and next-business-day handling

## Emergency Contacts

- Platform on-call: `platform-oncall@example.com`
- Backend on-call: `backend-oncall@example.com`
- Security escalation: `security@example.com`
- Leadership escalation: `engineering-leadership@example.com`
