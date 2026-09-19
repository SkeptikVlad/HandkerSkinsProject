package org.example.handkerskinsproject.service;

import lombok.extern.slf4j.Slf4j;
import org.example.handkerskinsproject.entity.SkinEntity;
import org.example.handkerskinsproject.fetcher.MarketCsgoFetcherClient;
import org.example.handkerskinsproject.repository.SkinRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TopOrderCacheService {

    private final MarketCsgoFetcherClient fetcherClient;
    private final SkinRepository skinRepository;

    private final AtomicReference<Map<String, BigDecimal>> topOrderByHashName =
            new AtomicReference<>(Map.of());

    public TopOrderCacheService(MarketCsgoFetcherClient fetcherClient, SkinRepository skinRepository) {
        this.fetcherClient = fetcherClient;
        this.skinRepository = skinRepository;
    }

    @Scheduled(fixedRate = 7000)
    public void refresh() {
        try {
            Set<String> trackedNames = skinRepository.findAll().stream()
                    .map(SkinEntity::getMarketHashName)
                    .collect(Collectors.toSet());

            if (trackedNames.isEmpty()) {
                return;
            }

            Map<String, BigDecimal> allOrders = fetcherClient.fetchTopOrdersMap();

            Map<String, BigDecimal> filtered = allOrders.entrySet().stream()
                    .filter(e -> trackedNames.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            topOrderByHashName.set(filtered);
            log.info("Кэш топ-ордеров обновлён: {} из {} отслеживаемых скинов имеют активный ордер",
                    filtered.size(), trackedNames.size());
            log.debug("Кэш топ-ордеров (полная карта): {}", filtered);
        } catch (Exception e) {
            log.error("Не удалось обновить кэш топ-ордеров: {}", e.getMessage());
        }
    }

    public BigDecimal getTopOrder(String marketHashName) {
        return topOrderByHashName.get().get(marketHashName);
    }
}