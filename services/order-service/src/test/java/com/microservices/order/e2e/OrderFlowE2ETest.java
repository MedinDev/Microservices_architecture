package com.microservices.order.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Requires running full stack through API gateway")
class OrderFlowE2ETest {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String gatewayBaseUrl = System.getProperty("phase5.gateway.url", "http://localhost:8080");

    @Test
    void orderCreationToNotificationFlow() throws IOException, InterruptedException {
        String orderPayload = """
            {
              "userId": 701,
              "items": [
                {"productCode":"SKU-701","quantity":2,"unitPrice":19.99}
              ]
            }
            """;
        HttpResponse<String> createOrder = post("/api/orders", orderPayload);
        assertEquals(200, createOrder.statusCode());

        HttpResponse<String> notifications = get("/api/notifications/user/701");
        assertTrue(notifications.statusCode() == 200 || notifications.statusCode() == 204);
    }

    @Test
    void paymentFailureCompensationFlow() throws IOException, InterruptedException {
        String orderPayload = """
            {
              "userId": 705,
              "items": [
                {"productCode":"SKU-FAIL","quantity":1,"unitPrice":50.00}
              ]
            }
            """;
        HttpResponse<String> createOrder = post("/api/orders", orderPayload);
        assertEquals(200, createOrder.statusCode());
    }

    @Test
    void multipleConcurrentOrders() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int userId = 800 + i;
                tasks.add(() -> {
                    String payload = """
                        {
                          "userId": %d,
                          "items": [
                            {"productCode":"SKU-CONC","quantity":1,"unitPrice":9.99}
                          ]
                        }
                        """.formatted(userId);
                    return post("/api/orders", payload).statusCode();
                });
            }

            long successCount = executor.invokeAll(tasks).stream().filter(future -> {
                try {
                    return future.get() == 200;
                } catch (Exception ex) {
                    return false;
                }
            }).count();

            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
            assertEquals(20, successCount);
        } finally {
            executor.shutdownNow();
        }
    }

    private HttpResponse<String> post(String path, String jsonPayload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gatewayBaseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gatewayBaseUrl + path))
            .GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
