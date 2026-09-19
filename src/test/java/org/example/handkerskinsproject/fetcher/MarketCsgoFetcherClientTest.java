package org.example.handkerskinsproject.fetcher;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.example.handkerskinsproject.dto.MarketItemPriceDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketCsgoFetcherClientTest {

    private MockWebServer mockWebServer;
    private MarketCsgoFetcherClient fetcherClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory());

        fetcherClient = new MarketCsgoFetcherClient(
                builder, mockWebServer.url("/").toString().replaceAll("/$", ""), "test-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchAllPrices_returnsParsedResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"items\":[]}"));

        var response = fetcherClient.fetchAllPrices();

        assertTrue(response.success());
        assertTrue(response.items().isEmpty());
    }

    @Test
    void fetchPricesMap_buildsMapByHashNameAndKeepsFirstOnDuplicate() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"success":true,"items":[
                          {"market_hash_name":"AK-47 | Redline (Field-Tested)","price":1000,"volume":5},
                          {"market_hash_name":"AK-47 | Redline (Field-Tested)","price":2000,"volume":1}
                        ]}
                        """));

        Map<String, MarketItemPriceDto> result = fetcherClient.fetchPricesMap();

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("1000"), result.get("AK-47 | Redline (Field-Tested)").minPrice());
    }

    @Test
    void fetchPricesMap_onServerError_returnsEmptyMap() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertTrue(fetcherClient.fetchPricesMap().isEmpty());
    }

    @Test
    void fetchTopOrdersMap_parsesOrdersAndDefaultsNullToZero() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"success":true,"items":[
                          {"market_hash_name":"AK-47 | Redline (Field-Tested)","price":900},
                          {"market_hash_name":"AWP | Asiimov (Field-Tested)","price":null}
                        ]}
                        """));

        Map<String, BigDecimal> result = fetcherClient.fetchTopOrdersMap();

        assertEquals(new BigDecimal("900"), result.get("AK-47 | Redline (Field-Tested)"));
        assertEquals(BigDecimal.ZERO, result.get("AWP | Asiimov (Field-Tested)"));
    }

    @Test
    void fetchTopOrdersMap_onServerError_returnsEmptyMap() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertTrue(fetcherClient.fetchTopOrdersMap().isEmpty());
    }

    @Test
    void sendPing_sendsRequestWithApiKey() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        fetcherClient.sendPing();

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("key=test-key"));
        assertTrue(request.getPath().contains("v=2"));
    }
}