package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderRequest(
        @NotBlank(message = "customerId is required")
        String customerId,

        @NotNull(message = "amountUSD is required")
        @Positive(message = "amountUSD must be positive")
        BigDecimal amountUSD,

        @NotBlank(message = "targetCurrency is required")
        @Size(min = 3, max = 3, message = "targetCurrency must be a 3-letter ISO code")
        String targetCurrency
) {
}
