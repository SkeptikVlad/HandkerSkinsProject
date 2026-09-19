package org.example.handkerskinsproject.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MarketAuthServiceTest {

    private MockWebServer mockWebServer;
    private MarketAuthService authService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory());

        authService = new MarketAuthService(builder);
        ReflectionTestUtils.setField(authService, "baseUrl", mockWebServer.url("/").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(authService, "apiKey", "test-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchFreshWsToken_returnsTokenOnSuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"token\":\"abc123\"}"));

        String token = authService.fetchFreshWsToken();

        assertEquals("abc123", token);
    }

    @Test
    void fetchFreshWsToken_throwsWhenSuccessIsFalse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":false,\"error\":\"bad key\"}"));

        assertThrows(RuntimeException.class, () -> authService.fetchFreshWsToken());
    }
}