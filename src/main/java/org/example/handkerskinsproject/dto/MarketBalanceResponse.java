package org.example.handkerskinsproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record MarketBalanceResponse(
        BigDecimal money,
        @JsonProperty("money_settlement") BigDecimal moneySettlement,
        String currency,
        boolean success
) {}