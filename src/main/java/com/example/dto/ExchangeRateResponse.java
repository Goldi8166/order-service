package com.example.dto;

import java.util.Map;

public record ExchangeRateResponse(
        String result,
        String base_code,
        Map<String, Double> rates
) {
}
