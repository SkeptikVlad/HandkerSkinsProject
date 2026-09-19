package org.example.handkerskinsproject.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.handkerskinsproject.notification.TelegramNotificationService;
import org.example.handkerskinsproject.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketWebSocketClientTest {

    @Mock private MarketAuthService authService;
    @Mock private SkinPriceService skinPriceService;
    @Mock private NameDictionaryService nameDictionary;
    @Mock private TopOrderCacheService topOrderCache;
    @Mock private MinPriceCacheService minPriceCache;   // ← НОВЫЙ мок
    @Mock private MarketBuyService buyService;
    @Mock private TelegramNotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // выполняем "асинхронную" покупку синхронно, чтобы тест не гонялся за потоками
    private final ExecutorService directExecutor = new AbstractExecutorService() {
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
    };

    private MarketWebSocketClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new MarketWebSocketClient(
                new URI("wss://example.invalid/ws"),
                authService, objectMapper, skinPriceService, nameDictionary, topOrderCache,
                minPriceCache,   // ← НОВЫЙ аргумент
                buyService, notificationService, directExecutor
        );
        // имитируем, что подписка прошла давно — иначе warm-up-гейт блокирует любой кандидат
        Field subscribedAtMillisField = MarketWebSocketClient.class.getDeclaredField("subscribedAtMillis");
        subscribedAtMillisField.setAccessible(true);
        subscribedAtMillisField.set(client, System.currentTimeMillis() - 61_000);
    }

    private String wsMessage(String event, long nameId, String price, String itemId) {
        StringBuilder data = new StringBuilder();
        data.append("\"event\":\"").append(event).append("\",");
        data.append("\"name_id\":").append(nameId).append(",");
        data.append("\"price\":\"").append(price).append("\"");
        if (itemId != null) {
            data.append(",\"id\":\"").append(itemId).append("\"");
        }
        return "{\"push\":{\"pub\":{\"data\":{" + data + "}}}}";
    }

    @Test
    void duringWarmup_doesNotBuyEvenIfPriceMatches() throws Exception {
        MarketWebSocketClient freshClient = new MarketWebSocketClient(
                new URI("wss://example.invalid/ws"),
                authService, objectMapper, skinPriceService, nameDictionary, topOrderCache,
                minPriceCache,
                buyService, notificationService, directExecutor
        );
        Field field = MarketWebSocketClient.class.getDeclaredField("subscribedAtMillis");
        field.setAccessible(true);
        field.set(freshClient, System.currentTimeMillis());

        freshClient.onMessage(wsMessage("new", 42L, "1050", "lot-warmup"));

        verifyNoInteractions(buyService, notificationService);
    }

    @Test
    void priceInsideMarginBand_triggersBuyAndSuccessNotification() {
        when(nameDictionary.resolve(42L)).thenReturn("AK-47 | Redline (Field-Tested)");
        when(topOrderCache.getTopOrder("AK-47 | Redline (Field-Tested)")).thenReturn(new BigDecimal("1000"));
        when(buyService.buyItem(eq("lot-1"), any(BigDecimal.class)))
                .thenReturn(new MarketBuyService.BuyResult(true, "OK", "auto-123"));

        client.onMessage(wsMessage("new", 42L, "1050", "lot-1"));

        verify(buyService).buyItem(eq("lot-1"), eq(new BigDecimal("1050")));
        verify(notificationService).sendNotification(contains("ЛОТ ВЫКУПЛЕН"));
    }

    @Test
    void priceBelowMarginBand_doesNotBuy() {
        when(nameDictionary.resolve(42L)).thenReturn("AK-47 | Redline (Field-Tested)");
        when(topOrderCache.getTopOrder("AK-47 | Redline (Field-Tested)")).thenReturn(new BigDecimal("1000"));

        client.onMessage(wsMessage("new", 42L, "1010", "lot-2")); // < 1030

        verifyNoInteractions(buyService);
        verifyNoInteractions(notificationService);
    }

    @Test
    void priceAboveMarginBand_doesNotBuy() {
        when(nameDictionary.resolve(42L)).thenReturn("AK-47 | Redline (Field-Tested)");
        when(topOrderCache.getTopOrder("AK-47 | Redline (Field-Tested)")).thenReturn(new BigDecimal("1000"));

        client.onMessage(wsMessage("new", 42L, "1200", "lot-3")); // > 1070

        verifyNoInteractions(buyService);
    }

    @Test
    void unknownNameId_doesNotBuy() {
        when(nameDictionary.resolve(42L)).thenReturn(null);

        client.onMessage(wsMessage("new", 42L, "1050", "lot-4"));

        verifyNoInteractions(topOrderCache);
        verifyNoInteractions(buyService);
    }

    @Test
    void noCachedTopOrder_doesNotBuy() {
        when(nameDictionary.resolve(42L)).thenReturn("AK-47 | Redline (Field-Tested)");
        when(topOrderCache.getTopOrder("AK-47 | Redline (Field-Tested)")).thenReturn(null);

        client.onMessage(wsMessage("new", 42L, "1050", "lot-5"));

        verifyNoInteractions(buyService);
    }

    @Test
    void missingItemId_doesNotBuyAndDoesNotThrow() {
        assertDoesNotThrow(() -> client.onMessage(wsMessage("new", 42L, "1050", null)));

        verifyNoInteractions(nameDictionary);
        verifyNoInteractions(topOrderCache);
        verifyNoInteractions(buyService);
    }

    @Test
    void duplicateEventsForSameItemId_onlyBuysOnce() {
        when(nameDictionary.resolve(42L)).thenReturn("AK-47 | Redline (Field-Tested)");
        when(topOrderCache.getTopOrder("AK-47 | Redline (Field-Tested)")).thenReturn(new BigDecimal("1000"));
        when(buyService.buyItem(eq("lot-dup"), any(BigDecimal.class)))
                .thenReturn(new MarketBuyService.BuyResult(true, "OK", "auto-1"));

        client.onMessage(wsMessage("new", 42L, "1050", "lot-dup"));
        client.onMessage(wsMessage("change", 42L, "1050", "lot-dup"));

        verify(buyService, times(1)).buyItem(eq("lot-dup"), any(BigDecimal.class));
    }

    @Test
    void irrelevantEventType_doesNotBuy() {
        client.onMessage(wsMessage("sold", 42L, "1050", "lot-6"));

        verifyNoInteractions(nameDictionary);
        verifyNoInteractions(buyService);
    }
}