package org.example.handkerskinsproject.service;

import org.example.handkerskinsproject.dto.MarketItemPriceDto;
import org.example.handkerskinsproject.dto.MarketPricesResponse;
import org.example.handkerskinsproject.entity.SkinEntity;
import org.example.handkerskinsproject.entity.SkinPriceHistoryEntity;
import org.example.handkerskinsproject.fetcher.MarketCsgoFetcherClient;
import org.example.handkerskinsproject.repository.SkinPriceHistoryRepository;
import org.example.handkerskinsproject.repository.SkinRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SkinPriceService {

    private static final Logger log = LoggerFactory.getLogger(SkinPriceService.class);

    private final SkinRepository skinRepository;
    private final SkinPriceHistoryRepository historyRepository;
    private final MarketCsgoFetcherClient fetcherClient;

    private static final BigDecimal MIN_TRACK_PRICE = new BigDecimal("500");
    private static final BigDecimal MAX_TRACK_PRICE = new BigDecimal("15000");

    public SkinPriceService(
            SkinRepository skinRepository,
            SkinPriceHistoryRepository historyRepository,
            MarketCsgoFetcherClient fetcherClient) {
        this.skinRepository = skinRepository;
        this.historyRepository = historyRepository;
        this.fetcherClient = fetcherClient;
    }

    @Transactional(readOnly = true)
    public List<SkinEntity> getAllTrackedSkins() {
        return skinRepository.findAll();
    }

    @Transactional
    public void syncPrices() {
        List<SkinEntity> trackedSkins = skinRepository.findAll();
        if (trackedSkins.isEmpty()) {
            log.info("Таблица отслеживаемых скинов пуста. Синхронизация пропущена.");
            return;
        }

        log.info("Загрузка цен с Market.CSGO для {} скинов...", trackedSkins.size());

        MarketPricesResponse response = fetcherClient.fetchAllPrices();
        if (response == null || !response.success() || response.items() == null) {
            log.error("Не удалось получить цены от Market.CSGO");
            return;
        }

        Map<String, MarketItemPriceDto> itemsMap = response.items().stream()
                .collect(Collectors.toMap(
                        MarketItemPriceDto::marketHashName,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        Map<String, MarketItemPriceDto> pricesMap = fetcherClient.fetchPricesMap();
        Map<String, BigDecimal> ordersMap = fetcherClient.fetchTopOrdersMap();

        for (SkinEntity skin : trackedSkins) {
            MarketItemPriceDto priceDto = pricesMap.get(skin.getMarketHashName());
            BigDecimal topOrder = ordersMap.getOrDefault(skin.getMarketHashName(), BigDecimal.ZERO);

            if (priceDto != null) {
                SkinPriceHistoryEntity history = new SkinPriceHistoryEntity();
                history.setSkin(skin);
                history.setMinPrice(priceDto.minPrice() != null ? priceDto.minPrice() : BigDecimal.ZERO);
                history.setVolume(priceDto.volume() != null ? priceDto.volume() : 0);
                history.setTopOrder(topOrder);

                historyRepository.save(history);
            }
        }
    }

    @Transactional
    public SkinEntity addSkinToTrack(String marketHashName) {
        return skinRepository.findByMarketHashName(marketHashName)
                .orElseGet(() -> {
                    BigDecimal price = resolveCurrentPrice(marketHashName);
                    validatePriceInRange(marketHashName, price);
                    return skinRepository.save(new SkinEntity(marketHashName));
                });
    }

    private BigDecimal resolveCurrentPrice(String marketHashName) {
        MarketItemPriceDto dto = fetcherClient.fetchPricesMap().get(marketHashName);
        if (dto == null || dto.minPrice() == null) {
            throw new IllegalArgumentException(
                    "Не удалось найти цену для \"" + marketHashName + "\" — проверь точное название скина");
        }
        return dto.minPrice();
    }

    private void validatePriceInRange(String marketHashName, BigDecimal price) {
        if (price.compareTo(MIN_TRACK_PRICE) < 0 || price.compareTo(MAX_TRACK_PRICE) > 0) {
            throw new IllegalArgumentException(String.format(
                    "%s стоит %s₽ — вне диапазона отслеживания [%s₽, %s₽]",
                    marketHashName, price, MIN_TRACK_PRICE, MAX_TRACK_PRICE));
        }
    }

    @Transactional
    public List<SkinEntity> importSkinsInPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        Map<String, MarketItemPriceDto> pricesMap = fetcherClient.fetchPricesMap();
        if (pricesMap.isEmpty()) {
            log.warn("Массовый импорт: не удалось получить список цен");
            return List.of();
        }

        Set<String> alreadyTracked = skinRepository.findAll().stream()
                .map(SkinEntity::getMarketHashName)
                .collect(Collectors.toSet());

        List<SkinEntity> toInsert = pricesMap.values().stream()
                .filter(dto -> dto.minPrice() != null
                        && dto.minPrice().compareTo(minPrice) >= 0
                        && dto.minPrice().compareTo(maxPrice) <= 0)
                .map(MarketItemPriceDto::marketHashName)
                .filter(name -> !isExcludedCategory(name))   // <-- новая строка
                .filter(name -> !alreadyTracked.contains(name))
                .distinct()
                .map(SkinEntity::new)
                .toList();


        if (toInsert.isEmpty()) {
            log.info("Массовый импорт: новых скинов в диапазоне {}-{}₽ не найдено", minPrice, maxPrice);
            return List.of();
        }

        List<SkinEntity> saved = skinRepository.saveAll(toInsert);
        log.info("Массовый импорт: добавлено {} новых скинов в диапазоне {}-{}₽", saved.size(), minPrice, maxPrice);
        return saved;
    }
    private static final Set<String> EXCLUDED_HASH_PREFIXES = Set.of(
            "Sticker | ",
            "Patch | ",
            "Music Kit | ",
            "StatTrak\u2122 Music Kit | ",
            "Sealed Graffiti | "
    );

    private static final Set<String> EXCLUDED_HASH_SUFFIXES = Set.of(
            " Case",
            " Capsule",
            " Pin",
            " Package"
    );

    private boolean isExcludedCategory(String marketHashName) {
        for (String prefix : EXCLUDED_HASH_PREFIXES) {
            if (marketHashName.startsWith(prefix)) {
                return true;
            }
        }
        for (String suffix : EXCLUDED_HASH_SUFFIXES) {
            if (marketHashName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}