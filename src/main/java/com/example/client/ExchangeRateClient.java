package com.example.client;

import com.example.dto.ExchangeRateResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Type-safe HTTP client proxy for open.er-api.com.
 * Base URL is bound via configKey "exchange-rate-api" in application.properties.
 */
@RegisterRestClient(configKey = "exchange-rate-api")
public interface ExchangeRateClient {

    @GET
    @Path("/v6/latest/USD")
    ExchangeRateResponse getLatestRates();
}
