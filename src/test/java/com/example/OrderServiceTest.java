package com.example;

import com.example.client.ExchangeRateClient;
import com.example.dto.ExchangeRateResponse;
import com.example.dto.OrderRequest;
import com.example.dto.OrderResponse;
import com.example.exception.ExternalApiException;
import com.example.exception.InvalidCurrencyException;
import com.example.service.OrderService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class OrderServiceTest {

    @Inject
    OrderService orderService;

    @InjectMock
    @RestClient
    ExchangeRateClient exchangeRateClient;

    @Test
    void createOrder_calculatesConvertedAmountCorrectly() {
        Mockito.when(exchangeRateClient.getLatestRates())
                .thenReturn(new ExchangeRateResponse("success", "USD", Map.of("EUR", 0.9233)));

        OrderRequest req = new OrderRequest("CUST-1001", new BigDecimal("150.00"), "EUR");
        OrderResponse resp = orderService.createOrder(req);

        // 150.00 * 0.9233 = 138.495 -> rounds to 138.50 (HALF_UP, 2 decimal places)
        assertEquals(new BigDecimal("138.50"), resp.convertedAmount());
        assertEquals("PROCESSED", resp.status());
        assertEquals("CUST-1001", resp.customerId());
        assertEquals("EUR", resp.targetCurrency());
    }

    @Test
    void createOrder_unknownCurrency_throwsInvalidCurrencyException() {
        Mockito.when(exchangeRateClient.getLatestRates())
                .thenReturn(new ExchangeRateResponse("success", "USD", Map.of("EUR", 0.92)));

        OrderRequest req = new OrderRequest("CUST-1001", new BigDecimal("100"), "XXX");

        assertThrows(InvalidCurrencyException.class, () -> orderService.createOrder(req));
    }

    @Test
    void createOrder_externalApiFails_throwsExternalApiException() {
        Mockito.when(exchangeRateClient.getLatestRates())
                .thenThrow(new RuntimeException("connection refused"));

        OrderRequest req = new OrderRequest("CUST-1001", new BigDecimal("100"), "EUR");

        assertThrows(ExternalApiException.class, () -> orderService.createOrder(req));
    }

    @Test
    void createOrder_apiReturnsFailureResult_throwsExternalApiException() {
        Mockito.when(exchangeRateClient.getLatestRates())
                .thenReturn(new ExchangeRateResponse("error", "USD", null));

        OrderRequest req = new OrderRequest("CUST-1001", new BigDecimal("100"), "EUR");

        assertThrows(ExternalApiException.class, () -> orderService.createOrder(req));
    }
}
