package org.example.handkerskinsproject.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class NameDictionaryService {

    private final RestClient restClient;

    @Value("${market-csgo.dictionary-url:https://market.csgo.com/api/v2/dictionary/names.json}")
    private String dictionaryUrl;

    private final AtomicReference<Map<Long, String>> nameById = new AtomicReference<>(Map.of());

    public NameDictionaryService(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        refresh();
    }

    @Scheduled(initialDelay = 24 * 60 * 60 * 1000, fixedRate = 24 * 60 * 60 * 1000)
    public void refresh() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = restClient.get()
                    .uri(dictionaryUrl)
                    .retrieve()
                    .body(Map.class);

            if (raw == null) {
                log.warn("Словарь name_id -> hash_name пуст, оставляю старую версию");
                return;
            }

            Object itemsNode = raw.containsKey("items") ? raw.get("items") : raw;

            Map<Long, String> parsed = new HashMap<>();

            if (itemsNode instanceof List<?> itemsList) {
                for (Object item : itemsList) {
                    if (!(item instanceof Map<?, ?> itemMap)) {
                        continue;
                    }
                    Object idObj = itemMap.get("id");
                    Object hashNameObj = itemMap.get("hash_name");
                    if (idObj == null || hashNameObj == null) {
                        continue;
                    }
                    try {
                        long nameId = Long.parseLong(String.valueOf(idObj));
                        parsed.put(nameId, String.valueOf(hashNameObj));
                    } catch (NumberFormatException ignored) {
                        // пропускаем некорректные записи
                    }
                }
            } else if (itemsNode instanceof Map<?, ?> itemsMap) {
                for (Map.Entry<?, ?> entry : itemsMap.entrySet()) {
                    try {
                        long nameId = Long.parseLong(String.valueOf(entry.getKey()));
                        parsed.put(nameId, String.valueOf(entry.getValue()));
                    } catch (NumberFormatException ignored) {
                        // пропускаем служебные поля вроде "success"
                    }
                }
            } else {
                log.warn("Неожиданный тип items: {}", itemsNode == null ? "null" : itemsNode.getClass());
                return;
            }

            if (parsed.isEmpty()) {
                log.warn("После парсинга словарь пуст, оставляю старую версию");
                return;
            }

            nameById.set(parsed);
            log.info("Словарь name_id -> hash_name обновлён, записей: {}", parsed.size());
        } catch (Exception e) {
            log.error("Не удалось обновить словарь name_id -> hash_name: {}", e.getMessage());
        }
    }

    public String resolve(long nameId) {
        return nameById.get().get(nameId);
    }
}