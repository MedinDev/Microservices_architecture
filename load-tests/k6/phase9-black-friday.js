import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.GATEWAY_URL || "http://localhost:8080";
const TOKEN = __ENV.BEARER_TOKEN || "";
const USER_ID = Number(__ENV.USER_ID || 2001);

export const options = {
  scenarios: {
    black_friday_surge: {
      executor: "ramping-arrival-rate",
      startRate: Number(__ENV.BF_START_RPS || 80),
      timeUnit: "1s",
      preAllocatedVUs: Number(__ENV.BF_PREALLOCATED_VUS || 120),
      maxVUs: Number(__ENV.BF_MAX_VUS || 600),
      stages: [
        { target: Number(__ENV.BF_WARMUP_RPS || 140), duration: __ENV.BF_WARMUP_DURATION || "8m" },
        { target: Number(__ENV.BF_PEAK_RPS || 280), duration: __ENV.BF_PEAK_DURATION || "20m" },
        { target: Number(__ENV.BF_SUSTAIN_RPS || 220), duration: __ENV.BF_SUSTAIN_DURATION || "10m" },
        { target: 0, duration: __ENV.BF_RAMPDOWN_DURATION || "3m" }
      ]
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.015"],
    http_req_duration: ["p(90)<400", "p(95)<650", "p(99)<1200"],
    checks: ["rate>0.985"]
  }
};

function orderPayload() {
  const suffix = `${__VU}-${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  return JSON.stringify({
    userId: USER_ID,
    items: [
      { productCode: `BF-SKU-${suffix}`, quantity: 1, unitPrice: 49.99 },
      { productCode: `BF-ACC-${suffix}`, quantity: 2, unitPrice: 9.99 }
    ]
  });
}

export default function () {
  const headers = {
    "Content-Type": "application/json"
  };
  if (TOKEN) {
    headers.Authorization = `Bearer ${TOKEN}`;
  }
  const createOrder = http.post(`${BASE_URL}/api/v1/orders`, orderPayload(), { headers });
  check(createOrder, {
    "black friday order accepted": (r) => r.status === 200
  });
  sleep(0.1);
}
