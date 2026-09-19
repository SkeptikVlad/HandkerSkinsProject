package org.example.handkerskinsproject.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class TelegramNotificationService {

    private final TelegramClient telegramClient;
    private final String chatId;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    public TelegramNotificationService(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.chat-id}") String chatId) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.chatId = chatId;
        Thread dispatcher = new Thread(this::consumeLoop, "telegram-dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    public void sendNotification(String text) {
        queue.offer(text);
    }

    private void consumeLoop() {
        while (true) {
            try {
                String text = queue.take();
                sendWithRetry(text, 3);
                Thread.sleep(200); // не больше ~5 сообщений в секунду
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void sendWithRetry(String text, int attemptsLeft) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId).text(text).parseMode("HTML").build();
        try {
            telegramClient.execute(message);
            log.info("Уведомление отправлено в Telegram!");
        } catch (TelegramApiException e) {
            log.warn("Ошибка отправки в Telegram: {}", e.getMessage());
            if (attemptsLeft > 1) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                sendWithRetry(text, attemptsLeft - 1);
            } else {
                log.error("Сообщение потеряно после нескольких попыток отправки в Telegram");
            }
        }
    }
}