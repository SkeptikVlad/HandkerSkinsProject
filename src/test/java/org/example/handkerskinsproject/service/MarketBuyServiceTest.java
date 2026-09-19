package org.example.handkerskinsproject.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MarketBuyServiceTest {

    private MockWebServer mockWebServer;
    private MarketBuyService buyService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory());

        buyService = new MarketBuyService(builder);
        ReflectionTestUtils.setField(buyService, "baseUrl",
                mockWebServer.url("/").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(buyService, "apiKey", "test-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void buyItem_sendsCorrectRequestAndParsesSuccess() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true}"));

        MarketBuyService.BuyResult result = buyService.buyItem("123456789012345", new BigDecimal("150.50"));

        assertTrue(result.success());
        assertEquals("OK", result.message());
        assertTrue(result.customId().startsWith("auto-"));

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("id=123456789012345"));
        assertTrue(request.getPath().contains("price=15050")); // 150.50 руб -> 15050 копеек
        assertTrue(request.getPath().contains("key=test-key"));
    }

    @Test
    void buyItem_returnsFailureWhenMarketReportsError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":false,\"error\":\"item_not_found\"}"));

        MarketBuyService.BuyResult result = buyService.buyItem("999", new BigDecimal("10.00"));

        assertFalse(result.success());
        assertEquals("item_not_found", result.message());
    }

    @Test
    void buyItem_handlesNetworkFailureGracefully() throws IOException {
        mockWebServer.shutdown(); // сервер недоступен

        MarketBuyService.BuyResult result = buyService.buyItem("1", new BigDecimal("5.00"));

        assertFalse(result.success());
        assertNotNull(result.message());
    }
}