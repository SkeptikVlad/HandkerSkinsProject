package org.example.handkerskinsproject.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.handkerskinsproject.notification.TelegramNotificationService;
import org.example.handkerskinsproject.service.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
public class MarketWsManager {

    private final MarketAuthService authService;
    private final ObjectMapper objectMapper;
    private final SkinPriceService skinPriceService;
    private final NameDictionaryService nameDictionary;
    private final TopOrderCacheService topOrderCache;
    private final MarketBuyService buyService;
    private final MinPriceCacheService minPriceCache;
    private final TelegramNotificationService notificationService;
    private final ExecutorService buyExecutorService;

    @Value("${market-csgo.ws.url:wss://wsprice.csgo.com/connection/websocket}")
    private String wsUrl;

    private MarketWebSocketClient client;

    public MarketWsManager(MarketAuthService authService,
                           ObjectMapper objectMapper,
                           SkinPriceService skinPriceService,
                           NameDictionaryService nameDictionary,
                           TopOrderCacheService topOrderCache,
                           MinPriceCacheService minPriceCache,
                           MarketBuyService buyService,
                           TelegramNotificationService notificationService,
                           @Qualifier("buyExecutorService") ExecutorService buyExecutorService) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.skinPriceService = skinPriceService;
        this.nameDictionary = nameDictionary;
        this.topOrderCache = topOrderCache;
        this.minPriceCache = minPriceCache;
        this.buyService = buyService;
        this.notificationService = notificationService;
        this.buyExecutorService = buyExecutorService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        try {
            log.info("Инициализация WebSocket соединения к {}", wsUrl);
            URI serverUri = URI.create(wsUrl);
            this.client = new MarketWebSocketClient(
                    serverUri, authService, objectMapper, skinPriceService, nameDictionary, topOrderCache,
                    minPriceCache,   // ← НОВЫЙ аргумент
                    buyService, notificationService, buyExecutorService
            );
            this.client.connect();
        } catch (Exception e) {
            log.error("Не удалось запустить WebSocket клиент", e);
        }
    }
}