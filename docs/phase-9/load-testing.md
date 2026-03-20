# Phase 9.1 Load Testing

## Scenarios

- Peak load simulation: `load-tests/k6/phase9-peak-load.js`
- Black Friday scenario: `load-tests/k6/phase9-black-friday.js`
- Gradual load increase: `load-tests/k6/phase9-gradual-ramp.js`
- Spike testing: `load-tests/k6/phase9-spike.js`
- Performance baseline: `load-tests/k6/phase9-baseline.js`

## Response Time SLOs

- API Gateway p95 latency: `< 500ms`
- Order create/read p95 latency: `< 650ms`
- Payment read p95 latency: `< 650ms`
- Notification read p95 latency: `< 500ms`
- End-to-end failure rate: `< 1%`

## Throughput Benchmarks

- Baseline workload: `20 VUs`, p95 `< 450ms`
- Peak workload: `120 RPS` sustained for `10m`
- Black Friday target: `280 RPS` peak with staged ramp
- Spike resilience: burst to `400 VUs` with recovery under `3m`

## Resource Utilization Limits

- API Gateway CPU target utilization: `<= 65%`
- Order/Payment CPU target utilization: `<= 70%`
- Notification CPU target utilization: `<= 65%`
- Pod memory target envelope:
  - Gateway: `<= 2Gi`
  - Order/Payment: `<= 2Gi`
  - Notification: `<= 1.5Gi`

## Execution Commands

```bash
chmod +x load-tests/k6/run-phase9.sh
```

```bash
GATEWAY_URL=http://localhost:8080 BEARER_TOKEN=<token> load-tests/k6/run-phase9.sh baseline
GATEWAY_URL=http://localhost:8080 BEARER_TOKEN=<token> load-tests/k6/run-phase9.sh peak
GATEWAY_URL=http://localhost:8080 BEARER_TOKEN=<token> load-tests/k6/run-phase9.sh black-friday
GATEWAY_URL=http://localhost:8080 BEARER_TOKEN=<token> load-tests/k6/run-phase9.sh gradual-ramp
GATEWAY_URL=http://localhost:8080 BEARER_TOKEN=<token> load-tests/k6/run-phase9.sh spike
```

## Result Artifacts

- Summary files are generated under `load-tests/k6/results/*.json`
- Track and record:
  - p90/p95/p99 latency
  - total RPS and dropped iterations
  - HTTP failure rate
  - SLO pass/fail by scenario
