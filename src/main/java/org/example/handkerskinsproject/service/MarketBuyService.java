package org.example.handkerskinsproject.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class MarketBuyService {

    private final RestClient restClient;
    private final BalanceCacheService balanceCache;

    @Value("${market-csgo.buy.dry-run:false}")
    private boolean dryRun;

    @Value("${market-csgo.api.base-url:https://market.csgo.com/api/v2}")
    private String baseUrl;

    @Value("${market-csgo.api.key}")
    private String apiKey;

    public MarketBuyService(RestClient.Builder restClientBuilder, BalanceCacheService balanceCache) {
        this.restClient = restClientBuilder.build();
        this.balanceCache = balanceCache;
    }

    public record BuyResult(boolean success, String message, String customId) {}

    public BuyResult buyItem(String itemId, BigDecimal priceRub) {
        String customId = "auto-" + UUID.randomUUID().toString().substring(0, 20);
        if (dryRun) {
            log.info("[DRY-RUN] Купил бы лот id={} по цене {} руб (запрос не отправлен)", itemId, priceRub);
            return new BuyResult(true, "DRY_RUN", customId);
        }

        if (!balanceCache.tryReserveFunds(priceRub)) {
            log.warn("Недостаточно баланса для покупки лота id={} по цене {} руб (закэшированный баланс: {})",
                    itemId, priceRub, balanceCache.getBalance());
            return new BuyResult(false, "INSUFFICIENT_BALANCE", customId);
        }

        long priceKopecks = priceRub
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();

        try {
            Map response = restClient.get()
                    .uri(baseUrl + "/buy?key={key}&id={id}&price={price}&custom_id={customId}",
                            apiKey, itemId, priceKopecks, customId)
                    .retrieve()
                    .body(Map.class);

            log.info("Ответ на покупку id={}: {}", itemId, response);

            boolean success = response != null && Boolean.TRUE.equals(response.get("success"));
            String message = success ? "OK" : String.valueOf(response != null ? response.get("error") : "empty response");

            if (!success) {
                balanceCache.releaseFunds(priceRub); // покупка не удалась — возвращаем резерв
            }

            return new BuyResult(success, message, customId);
        } catch (Exception e) {
            log.error("Ошибка при покупке лота id={}: {}", itemId, e.getMessage());
            balanceCache.releaseFunds(priceRub); // сеть/API упали — тоже возвращаем резерв
            return new BuyResult(false, e.getMessage(), customId);
        } finally {
            // сверяем закэшированный баланс с реальным в фоне, не задерживая ответ/уведомление
            Thread.ofVirtual().start(balanceCache::refresh);
        }
    }
}