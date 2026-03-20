import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.GATEWAY_URL || "http://localhost:8080";
const TOKEN = __ENV.BEARER_TOKEN || "";
const USER_ID = Number(__ENV.USER_ID || 1001);

export const options = {
  scenarios: {
    spike_test: {
      executor: "ramping-vus",
      stages: [
        { duration: "2m", target: 30 },
        { duration: "45s", target: 400 },
        { duration: "3m", target: 400 },
        { duration: "45s", target: 30 },
        { duration: "3m", target: 30 },
        { duration: "1m", target: 0 }
      ]
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.02"],
    http_req_duration: ["p(95)<800", "p(99)<1500"],
    checks: ["rate>0.98"]
  }
};

export default function () {
  const headers = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};
  const response = http.get(`${BASE_URL}/api/v1/notifications/user/${USER_ID}`, { headers });
  check(response, {
    "spike request acceptable": (r) => r.status === 200 || r.status === 204
  });
  sleep(0.15);
}
