package com.example;

import com.example.client.ExchangeRateClient;
import com.example.dto.ExchangeRateResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class OrderResourceIT {

    @InjectMock
    @RestClient
    ExchangeRateClient exchangeRateClient;

    @Test
    void postOrder_validRequest_returns201WithConvertedAmount() {
        Mockito.when(exchangeRateClient.getLatestRates())
                .thenReturn(new ExchangeRateResponse("success", "USD", Map.of("EUR", 0.92)));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"customerId":"CUST-1001","amountUSD":150.00,"targetCurrency":"EUR"}
                        """)
        .when()
                .post("/api/v1/orders")
        .then()
                .statusCode(201)
                .body("orderId", notNullValue())
                .body("customerId", equalTo("CUST-1001"))
                .body("targetCurrency", equalTo("EUR"))
                .body("status", equalTo("PROCESSED"));
    }

    @Test
    void postOrder_missingCustomerId_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"amountUSD":150.00,"targetCurrency":"EUR"}
                        """)
        .when()
                .post("/api/v1/orders")
        .then()
                .statusCode(400);
    }

    @Test
    void postOrder_negativeAmount_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"customerId":"CUST-1001","amountUSD":-10,"targetCurrency":"EUR"}
                        """)
        .when()
                .post("/api/v1/orders")
        .then()
                .statusCode(400);
    }

    @Test
    void postOrder_invalidCurrencyCode_returns400() {
        Mockito.when(exchangeRateClient.getLatestRates())
                .thenReturn(new ExchangeRateResponse("success", "USD", Map.of("EUR", 0.92)));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"customerId":"CUST-1001","amountUSD":100,"targetCurrency":"ZZZ"}
                        """)
        .when()
                .post("/api/v1/orders")
        .then()
                .statusCode(400);
    }
}
