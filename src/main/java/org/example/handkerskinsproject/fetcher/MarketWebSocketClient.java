package org.example.handkerskinsproject.fetcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.handkerskinsproject.notification.TelegramNotificationService;
import org.example.handkerskinsproject.service.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
public class MarketWebSocketClient extends WebSocketClient {

    private static final int CONNECT_ID = 1;
    private static final int SUBSCRIBE_ID = 2;
    private static final String CHANNEL = "public:items:730:rub";
    private static final long DEDUP_TTL_MILLIS = 5 * 60 * 1000;

    private final MarketAuthService authService;
    private final ObjectMapper objectMapper;
    private final SkinPriceService skinPriceService;
    private final NameDictionaryService nameDictionary;
    private final TopOrderCacheService topOrderCache;
    private final MinPriceCacheService minPriceCache;
    private final MarketBuyService buyService;
    private final TelegramNotificationService notificationService;
    private final ExecutorService buyExecutor;

    private final Map<String, Long> attemptedItemIds = new ConcurrentHashMap<>();

    private volatile boolean isClosedManually = false;
    private record MarginTier(BigDecimal maxTopOrder, BigDecimal minMargin, BigDecimal maxMargin) {}

    private static final long SKIN_COOLDOWN_MILLIS = 30_000;
    private static final long WARMUP_MILLIS = 60_000;

    private volatile long subscribedAtMillis = 0;
    private record SpreadTier(BigDecimal maxTopOrder, BigDecimal minResaleSpread) {}

    private static final List<SpreadTier> SPREAD_TIERS = List.of(
            new SpreadTier(new BigDecimal("1000"), new BigDecimal("1.30")),
            new SpreadTier(new BigDecimal("1500"), new BigDecimal("1.30")),
            new SpreadTier(new BigDecimal("3000"), new BigDecimal("1.20")),
            new SpreadTier(new BigDecimal("5000"), new BigDecimal("1.15")),
            new SpreadTier(new BigDecimal("7000"), new BigDecimal("1.15")),
            new SpreadTier(new BigDecimal("10000"), new BigDecimal("1.15")),
            new SpreadTier(new BigDecimal("15000"), new BigDecimal("1.15")),
            new SpreadTier(null,                   new BigDecimal("1.10"))
    );

    private final Map<String, Long> skinCooldown = new ConcurrentHashMap<>();


    public MarketWebSocketClient(URI serverUri,
                                 MarketAuthService authService,
                                 ObjectMapper objectMapper,
                                 SkinPriceService skinPriceService,
                                 NameDictionaryService nameDictionary,
                                 TopOrderCacheService topOrderCache,
                                 MinPriceCacheService minPriceCache,
                                 MarketBuyService buyService,
                                 TelegramNotificationService notificationService,
                                 ExecutorService buyExecutor) {
        super(serverUri);
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.skinPriceService = skinPriceService;
        this.nameDictionary = nameDictionary;
        this.topOrderCache = topOrderCache;
        this.minPriceCache = minPriceCache;
        this.buyService = buyService;
        this.notificationService = notificationService;
        this.buyExecutor = buyExecutor;
    }
    private static final List<MarginTier> MARGIN_TIERS = List.of(
            new MarginTier(new BigDecimal("1000"), new BigDecimal("1.01"), new BigDecimal("1.09")),
            new MarginTier(new BigDecimal("1500"), new BigDecimal("1.01"), new BigDecimal("1.09")),
            new MarginTier(new BigDecimal("3000"), new BigDecimal("1.01"), new BigDecimal("1.08")),
            new MarginTier(new BigDecimal("5000"), new BigDecimal("1.01"), new BigDecimal("1.06")),
            new MarginTier(new BigDecimal("7000"), new BigDecimal("1.005"), new BigDecimal("1.05")),
            new MarginTier(new BigDecimal("10000"), new BigDecimal("1.005"), new BigDecimal("1.04")),
            new MarginTier(new BigDecimal("15000"), new BigDecimal("1.005"), new BigDecimal("1.03")),
            new MarginTier(null,                    new BigDecimal("1.005"), new BigDecimal("1.03"))
    );


    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("WebSocket соединение установлено. Отправка connect...");
        try {
            String token = authService.fetchFreshWsToken();
            Map<String, Object> connectCommand = Map.of(
                    "id", CONNECT_ID,
                    "connect", Map.of("token", token)
            );
            send(objectMapper.writeValueAsString(connectCommand));
        } catch (Exception e) {
            log.error("Не удалось отправить connect с токеном: {}", e.getMessage());
            super.close();
        }
    }

    private boolean trySkinCooldown(String hashName) {
        long now = System.currentTimeMillis();
        Long last = skinCooldown.putIfAbsent(hashName, now);
        if (last != null) {
            if (now - last < SKIN_COOLDOWN_MILLIS) {
                return false;
            }
            skinCooldown.put(hashName, now);
        }
        return true;
    }

    @Override
    public void onMessage(String message) {
        try {
            if ("{}".equals(message)) {
                send("{}");
                return;
            }

            JsonNode root = objectMapper.readTree(message);

            if (root.has("id") && root.get("id").asInt() == CONNECT_ID) {
                if (root.has("error")) {
                    log.error("Ошибка авторизации при connect: {}", root.get("error"));
                    return;
                }
                Map<String, Object> subscribeCommand = Map.of(
                        "id", SUBSCRIBE_ID,
                        "subscribe", Map.of("channel", CHANNEL)
                );
                send(objectMapper.writeValueAsString(subscribeCommand));
                return;
            }

            if (root.has("id") && root.get("id").asInt() == SUBSCRIBE_ID) {
                if (root.has("error")) {
                    log.error("Ошибка подписки на канал {}: {}", CHANNEL, root.get("error"));
                } else {
                    log.info("Успешно подписались на канал {}", CHANNEL);
                    subscribedAtMillis = System.currentTimeMillis();
                }
                return;
            }

            JsonNode dataNode = extractPublicationData(root);
            if (dataNode != null) {
                handlePriceUpdate(dataNode);
            }

        } catch (Exception e) {
            log.debug("Нерелевантное или не подлежащее парсингу WS сообщение: {}", message, e);
        }
    }

    private JsonNode extractPublicationData(JsonNode root) {
        JsonNode push = root.path("push");
        JsonNode pub = push.path("pub");
        JsonNode data = pub.path("data");
        return data.isMissingNode() ? null : data;
    }
    private final java.util.concurrent.atomic.AtomicLong rawMessageCount = new java.util.concurrent.atomic.AtomicLong();

    private void handlePriceUpdate(JsonNode dataNode) {
        try {
            long count = rawMessageCount.incrementAndGet();
            if (count % 2000 == 0) {
                log.info("WS-диагностика: обработано {} сырых событий за {} мс с момента подписки",
                        count, System.currentTimeMillis() - subscribedAtMillis);
            }
            String event = dataNode.path("event").asText(null);
            if (!"new".equals(event) && !"change".equals(event)) {
                return;
            }
            if (subscribedAtMillis == 0
                    || System.currentTimeMillis() - subscribedAtMillis < WARMUP_MILLIS) {
                return;
            }

            long nameId = dataNode.path("name_id").asLong();
            BigDecimal price = new BigDecimal(dataNode.path("price").asText("0"));
            String itemId = dataNode.path("id").asText(null);

            if (itemId == null || itemId.isBlank()) {
                log.debug("WS событие без id лота, пропускаем: {}", dataNode);
                return;
            }

            String hashName = nameDictionary.resolve(nameId);
            if (hashName == null) {
                return;
            }
            /// log.info("[TEST] id={} price={} hashName={}", itemId, price, hashName);

            BigDecimal topOrder = topOrderCache.getTopOrder(hashName);
            if (topOrder == null || topOrder.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            BigDecimal minPrice = minPriceCache.getMinPrice(hashName);
            BigDecimal minResaleSpread = resolveSpreadTier(topOrder).minResaleSpread();
            if (minPrice == null || minPrice.compareTo(topOrder.multiply(minResaleSpread)) < 0) {
                return;
            }

            MarginTier tier = resolveMarginTier(topOrder);
            BigDecimal lowerBound = topOrder.multiply(tier.minMargin());
            BigDecimal upperBound = topOrder.multiply(tier.maxMargin());

            if (price.compareTo(lowerBound) >= 0 && price.compareTo(upperBound) <= 0) {
                if (!tryReserve(itemId)) {
                    log.debug("Лот {} уже в обработке/куплен, повторную попытку пропускаем", itemId);
                    return;
                }
                if (!trySkinCooldown(hashName)) {
                    log.debug("Скин {} уже обрабатывался недавно, пропускаем повторный лот {}", hashName, itemId);
                    return;
                }

                log.info("Кандидат на покупку: {} (id={}) за {} (topOrder={})", hashName, itemId, price, topOrder);
                long detectedAtNanos = System.nanoTime();

                buyExecutor.execute(() -> attemptPurchase(itemId, hashName, price, topOrder, minPrice, detectedAtNanos));            }
        } catch (Exception e) {
            log.debug("Ошибка обработки price update: {}", e.getMessage());
        }
    }

    private void attemptPurchase(String itemId, String hashName, BigDecimal price, BigDecimal topOrder, BigDecimal minPrice, long detectedAtNanos) {
        MarketBuyService.BuyResult result = buyService.buyItem(itemId, price);
        long elapsedMs = (System.nanoTime() - detectedAtNanos) / 1_000_000;

        BigDecimal overOrderPercent = price.subtract(topOrder)
                .divide(topOrder, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        BigDecimal resaleSpreadPercent = minPrice.subtract(topOrder)
                .divide(topOrder, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        boolean isDryRun = "DRY_RUN".equals(result.message());

        String message;
        if (result.success()) {
            message = String.format("""
        %s <b>ЛОТ ВЫКУПЛЕН%s</b>

        🔫 <b>Предмет:</b> %s
        💰 <b>Цена:</b> %s ₽
        📊 <b>top_order:</b> %s ₽ (+%s%%)
        📈 <b>мин. цена на рынке:</b> %s ₽ (спред +%s%% от top_order)
        🆔 <b>id лота:</b> %s
        ⏱ <b>Детект → ответ API:</b> %d мс
        %s
        """,
                    isDryRun ? "🧪" : "✅",
                    isDryRun ? " (DRY-RUN, реальная покупка НЕ отправлена)" : "",
                    hashName, price, topOrder, overOrderPercent, minPrice, resaleSpreadPercent, itemId, elapsedMs,
                    isDryRun ? "" : "Не забудь подтвердить сделку в Steam, если потребуется.");
            log.info("Лот {} {} за {} ({} мс)", itemId, isDryRun ? "был бы куплен (dry-run)" : "успешно куплен", price, elapsedMs);
        } else {
            message = String.format("""
                ❌ <b>ПОКУПКА НЕ УДАЛАСЬ</b>

                🔫 <b>Предмет:</b> %s
                💰 <b>Цена:</b> %s ₽
                📈 <b>мин. цена на рынке:</b> %s ₽
                🆔 <b>id лота:</b> %s
                ⚠️ <b>Причина:</b> %s
                ⏱ <b>Детект → ответ API:</b> %d мс
                """,
                    hashName, price, minPrice, itemId, result.message(), elapsedMs);
            log.warn("Не удалось купить лот {}: {} ({} мс)", itemId, result.message(), elapsedMs);
        }

        notificationService.sendNotification(message);
    }

    /**
     * Атомарно "бронирует" id лота под попытку покупки, чтобы повторные
     * new/change события по тому же лоту не запускали вторую попытку.
     */
    private boolean tryReserve(String itemId) {
        long now = System.currentTimeMillis();
        Long previous = attemptedItemIds.putIfAbsent(itemId, now);
        if (previous != null) {
            return false;
        }
        if (attemptedItemIds.size() > 5000) {
            attemptedItemIds.entrySet().removeIf(e -> now - e.getValue() > DEDUP_TTL_MILLIS);
        }
        return true;
    }

    public void shutdownPermanently() {
        this.isClosedManually = true;
        close();
    }


    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("WebSocket соединение закрыто. Код: {}, Причина: {}. Дистанционно: {}", code, reason, remote);
        if (!isClosedManually) {
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    if (!isClosedManually) {
                        log.info("Попытка переподключения WebSocket...");
                        this.reconnectBlocking();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Ошибка при переподключении к WebSocket: {}", e.getMessage());
                    super.close();
                }
            }).start();
        }
    }

    @Override
    public void onError(Exception ex) {
        log.error("Ошибка WebSocket: {}", ex.getMessage());
    }
    private MarginTier resolveMarginTier(BigDecimal topOrder) {
        for (MarginTier tier : MARGIN_TIERS) {
            if (tier.maxTopOrder() == null || topOrder.compareTo(tier.maxTopOrder()) <= 0) {
                return tier;
            }
        }
        return MARGIN_TIERS.get(MARGIN_TIERS.size() - 1);
    }
    private SpreadTier resolveSpreadTier(BigDecimal topOrder) {
        for (SpreadTier tier : SPREAD_TIERS) {
            if (tier.maxTopOrder() == null || topOrder.compareTo(tier.maxTopOrder()) <= 0) {
                return tier;
            }
        }
        return SPREAD_TIERS.get(SPREAD_TIERS.size() - 1);
    }
}