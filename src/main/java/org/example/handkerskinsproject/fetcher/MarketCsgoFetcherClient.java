package org.example.handkerskinsproject.fetcher;

import lombok.extern.slf4j.Slf4j;
import org.example.handkerskinsproject.dto.MarketItemPriceDto;
import org.example.handkerskinsproject.dto.MarketOrderDto;
import org.example.handkerskinsproject.dto.MarketOrdersFileResponse;
import org.example.handkerskinsproject.dto.MarketPricesResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MarketCsgoFetcherClient {

    private final RestClient restClient;
    private final String apiKey;

    public MarketCsgoFetcherClient(
            RestClient.Builder builder,
            @Value("${market-csgo.api.base-url}") String baseUrl,
            @Value("${market-csgo.api.key}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public MarketPricesResponse fetchAllPrices() {
        return restClient.get()
                .uri("/prices/RUB.json")
                .retrieve()
                .body(MarketPricesResponse.class);
    }

    public Map<String, MarketItemPriceDto> fetchPricesMap() {
        try {
            MarketPricesResponse response = fetchAllPrices();
            if (response != null && response.items() != null) {
                return response.items().stream()
                        .collect(Collectors.toMap(
                                MarketItemPriceDto::marketHashName,
                                dto -> dto,
                                (existing, replacement) -> existing
                        ));
            }
        } catch (Exception e) {
            log.error("Ошибка при загрузке файла цен: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    public Map<String, BigDecimal> fetchTopOrdersMap() {
        try {
            MarketOrdersFileResponse response = restClient.get()
                    .uri("/prices/orders/RUB.json") // Использован относительный путь
                    .retrieve()
                    .body(MarketOrdersFileResponse.class);

            if (response != null && response.items() != null) {
                return response.items().stream()
                        .collect(Collectors.toMap(
                                MarketOrderDto::marketHashName,
                                dto -> dto.topOrder() != null ? dto.topOrder() : BigDecimal.ZERO,
                                (existing, replacement) -> existing
                        ));
            }
        } catch (Exception e) {
            log.error("Ошибка при загрузке файла ордеров: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    public void sendPing() {
        restClient.get()
                .uri("/ping?key={key}&v=2", apiKey)
                .retrieve()
                .toBodilessEntity();
    }
}