package org.example.handkerskinsproject.service;

import lombok.extern.slf4j.Slf4j;
import org.example.handkerskinsproject.dto.MarketBalanceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class BalanceCacheService {

    private final RestClient restClient;

    @Value("${market-csgo.api.base-url:https://market.csgo.com/api/v2}")
    private String baseUrl;

    @Value("${market-csgo.api.key}")
    private String apiKey;
    private final AtomicReference<BigDecimal> cachedBalance = new AtomicReference<>(BigDecimal.ZERO);

    public BalanceCacheService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        refresh();
    }

    @Scheduled(fixedRate = 15_000)
    public void refresh() {
        try {
            MarketBalanceResponse response = restClient.get()
                    .uri(baseUrl + "/get-money?key=" + apiKey)
                    .retrieve()
                    .body(MarketBalanceResponse.class);

            if (response == null || !response.success() || response.money() == null) {
                log.warn("Не удалось обновить баланс: пустой или неуспешный ответ {}", response);
                return;
            }

            if (response.currency() != null && !"RUB".equalsIgnoreCase(response.currency())) {
                log.warn("Валюта аккаунта ({}) не RUB — сравнение баланса с рублёвыми ценами будет некорректным!",
                        response.currency());
            }

            cachedBalance.set(response.money());
            log.info("Баланс обновлён: {} {}", response.money(), response.currency());
        } catch (Exception e) {
            log.error("Не удалось обновить баланс: {}", e.getMessage());
        }
    }

    public BigDecimal getBalance() {
        return cachedBalance.get();
    }

    /** Атомарно: если хватает — сразу "резервирует" (вычитает) сумму и возвращает true. */
    public boolean tryReserveFunds(BigDecimal price) {
        while (true) {
            BigDecimal current = cachedBalance.get();
            if (current.compareTo(price) < 0) {
                return false;
            }
            BigDecimal updated = current.subtract(price);
            if (cachedBalance.compareAndSet(current, updated)) {
                return true;
            }
        }
    }

    /** Возврат ранее зарезервированной суммы, если покупка не удалась. */
    public void releaseFunds(BigDecimal amount) {
        cachedBalance.updateAndGet(current -> current.add(amount));
    }
}