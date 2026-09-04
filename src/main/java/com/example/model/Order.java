package com.example.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order extends PanacheEntity {

    public String customerId;

    public BigDecimal amountUSD;

    public String targetCurrency;

    public BigDecimal convertedAmount;

    @Enumerated(EnumType.STRING)
    public OrderStatus status;

    public Instant createdAt;
}
