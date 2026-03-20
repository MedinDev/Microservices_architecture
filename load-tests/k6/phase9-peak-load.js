import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.GATEWAY_URL || "http://localhost:8080";
const TOKEN = __ENV.BEARER_TOKEN || "";
const USER_ID = Number(__ENV.USER_ID || 1001);

export const options = {
  scenarios: {
    peak_traffic: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.PEAK_RPS || 120),
      timeUnit: "1s",
      duration: __ENV.PEAK_DURATION || "10m",
      preAllocatedVUs: Number(__ENV.PEAK_PREALLOCATED_VUS || 80),
      maxVUs: Number(__ENV.PEAK_MAX_VUS || 300)
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(90)<350", "p(95)<500", "p(99)<900"],
    checks: ["rate>0.99"]
  }
};

export default function () {
  const headers = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};
  const response = http.get(`${BASE_URL}/api/v1/notifications/user/${USER_ID}`, { headers });
  check(response, {
    "peak status ok": (r) => r.status === 200 || r.status === 204
  });
  sleep(0.2);
}
