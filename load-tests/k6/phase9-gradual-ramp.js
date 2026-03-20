import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.GATEWAY_URL || "http://localhost:8080";
const TOKEN = __ENV.BEARER_TOKEN || "";

export const options = {
  scenarios: {
    gradual_increase: {
      executor: "ramping-vus",
      stages: [
        { duration: "5m", target: 20 },
        { duration: "5m", target: 40 },
        { duration: "5m", target: 80 },
        { duration: "5m", target: 120 },
        { duration: "5m", target: 160 },
        { duration: "5m", target: 0 }
      ]
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<550", "p(99)<1000"],
    checks: ["rate>0.99"]
  }
};

export default function () {
  const headers = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};
  const orderId = Number(__ENV.ORDER_ID || 1);
  const paymentId = Number(__ENV.PAYMENT_ID || 1);
  const step = __ITER % 3;
  if (step === 0) {
    const response = http.get(`${BASE_URL}/api/v1/orders/${orderId}`, { headers });
    check(response, { "order read success": (r) => r.status === 200 });
  } else if (step === 1) {
    const response = http.get(`${BASE_URL}/api/v1/payments/${paymentId}`, { headers });
    check(response, { "payment read success": (r) => r.status === 200 });
  } else {
    const userId = Number(__ENV.USER_ID || 1001);
    const response = http.get(`${BASE_URL}/api/v1/notifications/user/${userId}`, { headers });
    check(response, { "notification read success": (r) => r.status === 200 || r.status === 204 });
  }
  sleep(0.3);
}
