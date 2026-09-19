package org.example.handkerskinsproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record MarketOrderDto(
        @JsonProperty("market_hash_name") String marketHashName,
        @JsonProperty("price") BigDecimal topOrder
) {}