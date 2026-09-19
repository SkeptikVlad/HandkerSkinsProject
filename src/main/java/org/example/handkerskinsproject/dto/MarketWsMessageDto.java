package org.example.handkerskinsproject.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketWsMessageDto(
        @JsonProperty("event") String event,        // "change", "new", "remove", "sold"
        @JsonProperty("id") String itemId,           // id лота на площадке
        @JsonProperty("class") String classId,       // Steam classid
        @JsonProperty("instance") String instanceId, // Steam instanceid
        @JsonProperty("name_id") Long nameId,        // числовой ID предмета — НЕ строка с названием!
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("asset") String assetId,
        @JsonProperty("inspect_url") String inspectUrl
) {}