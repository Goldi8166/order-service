package com.example.service;

import com.example.client.ExchangeRateClient;
import com.example.dto.ExchangeRateResponse;
import com.example.dto.OrderRequest;
import com.example.dto.OrderResponse;
import com.example.exception.ExternalApiException;
import com.example.exception.InvalidCurrencyException;
import com.example.model.Order;
import com.example.model.OrderStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @RestClient
    ExchangeRateClient exchangeRateClient;

    @Inject
    @Channel("order-events")
    MutinyEmitter<String> orderEventEmitter;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public OrderResponse createOrder(OrderRequest req) {
        ExchangeRateResponse rateResp = fetchRates();

        Double rate = rateResp.rates().get(req.targetCurrency().toUpperCase());
        if (rate == null) {
            throw new InvalidCurrencyException("Unsupported currency: " + req.targetCurrency());
        }

        BigDecimal convertedAmount = req.amountUSD()
                .multiply(BigDecimal.valueOf(rate))
                .setScale(2, RoundingMode.HALF_UP);

        Order order = new Order();
        order.customerId = req.customerId();
        order.amountUSD = req.amountUSD();
        order.targetCurrency = req.targetCurrency().toUpperCase();
        order.convertedAmount = convertedAmount;
        order.status = OrderStatus.PROCESSED;
        order.createdAt = Instant.now();
        order.persist();

        OrderResponse response = toResponse(order);
        publishOrderCreatedEvent(response);
        return response;
    }

    private ExchangeRateResponse fetchRates() {
        try {
            ExchangeRateResponse rateResp = exchangeRateClient.getLatestRates();
            if (rateResp == null || rateResp.rates() == null || !"success".equalsIgnoreCase(rateResp.result())) {
                throw new ExternalApiException("Exchange rate service returned an invalid response");
            }
            return rateResp;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Exchange rate service is unavailable: " + e.getMessage(), e);
        }
    }

    private void publishOrderCreatedEvent(OrderResponse response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            orderEventEmitter.sendAndForget(payload);
        } catch (JsonProcessingException e) {
            // Never fail order creation because the event couldn't be serialized/published.
            LOG.error("Failed to publish ORDER_CREATED event for order " + response.orderId(), e);
        }
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.id, o.customerId, o.amountUSD, o.targetCurrency,
                o.convertedAmount, o.status.name(), o.createdAt);
    }
}
