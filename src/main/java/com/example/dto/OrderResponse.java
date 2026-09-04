package com.example.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long orderId,
        String customerId,
        BigDecimal amountUSD,
        String targetCurrency,
        BigDecimal convertedAmount,
        String status,
        Instant createdAt
) {
}
