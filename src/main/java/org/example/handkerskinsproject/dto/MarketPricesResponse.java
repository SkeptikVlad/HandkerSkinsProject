package org.example.handkerskinsproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MarketPricesResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("items") List<MarketItemPriceDto> items
) {}