import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.GATEWAY_URL || "http://localhost:8080";
const TOKEN = __ENV.BEARER_TOKEN || "";

export const options = {
  vus: Number(__ENV.BASELINE_VUS || 20),
  duration: __ENV.BASELINE_DURATION || "5m",
  thresholds: {
    http_req_failed: ["rate<0.005"],
    http_req_duration: ["avg<250", "p(95)<450", "p(99)<900"],
    checks: ["rate>0.995"]
  }
};

export default function () {
  const headers = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};
  const orderId = Number(__ENV.ORDER_ID || 1);
  const response = http.get(`${BASE_URL}/api/v1/orders/${orderId}`, { headers });
  check(response, {
    "baseline order read": (r) => r.status === 200
  });
  sleep(0.4);
}
