package org.example.handkerskinsproject.service;

import lombok.extern.slf4j.Slf4j;
import org.example.handkerskinsproject.dto.MarketItemPriceDto;
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
public class MinPriceCacheService {

    private final MarketCsgoFetcherClient fetcherClient;
    private final SkinRepository skinRepository;

    private final AtomicReference<Map<String, BigDecimal>> minPriceByHashName =
            new AtomicReference<>(Map.of());

    public MinPriceCacheService(MarketCsgoFetcherClient fetcherClient, SkinRepository skinRepository) {
        this.fetcherClient = fetcherClient;
        this.skinRepository = skinRepository;
    }

    @Scheduled(fixedRate = 7000)
    public void refresh() {
        try {
            Set<String> trackedNames = skinRepository.findAll().stream()
                    .map(SkinEntity::getMarketHashName)
                    .collect(Collectors.toSet());
            if (trackedNames.isEmpty()) return;

            Map<String, MarketItemPriceDto> allPrices = fetcherClient.fetchPricesMap();

            Map<String, BigDecimal> filtered = allPrices.entrySet().stream()
                    .filter(e -> trackedNames.contains(e.getKey()) && e.getValue().minPrice() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().minPrice()));

            minPriceByHashName.set(filtered);
            log.info("Кэш минимальных цен обновлён: {} из {} отслеживаемых скинов",
                    filtered.size(), trackedNames.size());
        } catch (Exception e) {
            log.error("Не удалось обновить кэш минимальных цен: {}", e.getMessage());
        }
    }

    public BigDecimal getMinPrice(String marketHashName) {
        return minPriceByHashName.get().get(marketHashName);
    }
}
