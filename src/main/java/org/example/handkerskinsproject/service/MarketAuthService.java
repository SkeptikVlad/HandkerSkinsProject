package org.example.handkerskinsproject.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
public class MarketAuthService {

    private final RestClient restClient;

    @Value("${market-csgo.api.base-url:https://market.csgo.com/api/v2}")
    private String baseUrl;

    @Value("${market-csgo.api.key}")
    private String apiKey;

    public MarketAuthService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public String fetchFreshWsToken() {
        Map<String, Object> response = restClient.get()
                .uri(baseUrl + "/get-ws-token?key=" + apiKey)
                .retrieve()
                .body(Map.class);

        log.info("API get-ws-token response: {}", response);

        if (response != null && Boolean.TRUE.equals(response.get("success"))) {
            return (String) response.get("token"); // было "wsAuth"
        }
        throw new RuntimeException("Не удалось получить WS Auth Token: " + response);
    }
}