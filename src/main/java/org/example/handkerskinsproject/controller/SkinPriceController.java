package org.example.handkerskinsproject.controller;

import lombok.RequiredArgsConstructor;
import org.example.handkerskinsproject.entity.SkinEntity;
import org.example.handkerskinsproject.notification.TelegramNotificationService;
import org.example.handkerskinsproject.service.MarketBuyService;
import org.example.handkerskinsproject.service.SkinPriceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skins")
@RequiredArgsConstructor
public class SkinPriceController {

    private final SkinPriceService skinPriceService;
    private final MarketBuyService marketBuyService;
    private final TelegramNotificationService notificationService;

    @PostMapping("/test-buy")
    public ResponseEntity<String> testBuy(
            @RequestParam String itemId,
            @RequestParam java.math.BigDecimal price) {
        long startNanos = System.nanoTime();
        MarketBuyService.BuyResult result = marketBuyService.buyItem(itemId, price);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        String message = String.format(
                "🧪 <b>РУЧНОЙ ТЕСТ ПОКУПКИ</b>\n\n" +
                        "🔫 id лота: %s\n" +
                        "💰 Цена: %s ₽\n" +
                        "%s Результат: %s\n" +
                        "⏱ Время ответа API: %d мс",
                itemId, price,
                result.success() ? "✅" : "❌",
                result.message(), elapsedMs);

        notificationService.sendNotification(message);
        return ResponseEntity.ok("Результат: " + result.message() + " (" + elapsedMs + " мс)");
    }

    @GetMapping
    public ResponseEntity<List<SkinEntity>> getAllTrackedSkins() {
        return ResponseEntity.ok(skinPriceService.getAllTrackedSkins());
    }

    @PostMapping("/sync")
    public ResponseEntity<String> syncPrices() {
        skinPriceService.syncPrices();
        return ResponseEntity.ok("Синхронизация цен с Market.CSGO успешно запущена!");
    }

    @PostMapping
    public ResponseEntity<?> addSkinToTrack(@RequestParam("name") String name) {
        try {
            SkinEntity skin = skinPriceService.addSkinToTrack(name);
            return ResponseEntity.status(HttpStatus.CREATED).body(skin);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/import")
    public ResponseEntity<String> importSkins(
            @RequestParam(value = "min", defaultValue = "500") java.math.BigDecimal min,
            @RequestParam(value = "max", defaultValue = "15000") java.math.BigDecimal max) { // было 5000
        var imported = skinPriceService.importSkinsInPriceRange(min, max);
        return ResponseEntity.ok("Импортировано новых скинов: " + imported.size());
    }
}