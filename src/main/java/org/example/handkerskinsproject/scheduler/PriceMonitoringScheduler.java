package org.example.handkerskinsproject.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.handkerskinsproject.service.SkinPriceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceMonitoringScheduler {

    private final SkinPriceService skinPriceService;

    @Scheduled(initialDelay = 600000, fixedRate = 600000)
    public void monitorPrice() {
        try {
            skinPriceService.syncPrices();
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Skinport Rate Limit (429): Превышен лимит запросов. Следующая попытка через 10 минут.");
        } catch (Exception e) {
            log.error("Ошибка при обновлении цен: {}", e.getMessage(), e);
        }
    }
}