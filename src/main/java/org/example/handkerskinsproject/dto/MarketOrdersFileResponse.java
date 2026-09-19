package org.example.handkerskinsproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MarketOrdersFileResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("items") List<MarketOrderDto> items
) {}