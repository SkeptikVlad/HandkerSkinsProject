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

class NameDictionaryServiceTest {

    private MockWebServer mockWebServer;
    private NameDictionaryService service;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory());

        service = new NameDictionaryService(builder);
        ReflectionTestUtils.setField(service, "dictionaryUrl", mockWebServer.url("/dictionary.json").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void refresh_loadsListOfItemsAndResolveWorks() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"success":true,"time":1234567890,"items":[
                          {"id":628235,"hash_name":"#CSGO_crate_musickit_masterminds2_capsule"},
                          {"id":12345,"hash_name":"AK-47 | Redline (Field-Tested)"}
                        ]}
                        """));

        service.refresh();

        assertEquals("AK-47 | Redline (Field-Tested)", service.resolve(12345L));
        assertEquals("#CSGO_crate_musickit_masterminds2_capsule", service.resolve(628235L));
        assertNull(service.resolve(99999L));
    }

    @Test
    void resolve_beforeAnyRefresh_returnsNull() {
        assertNull(service.resolve(12345L));
    }

    @Test
    void refresh_onServerError_keepsPreviousDictionaryAndDoesNotThrow() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"items\":[{\"id\":12345,\"hash_name\":\"AK-47 | Redline (Field-Tested)\"}]}"));
        service.refresh();
        assertEquals("AK-47 | Redline (Field-Tested)", service.resolve(12345L));

        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertDoesNotThrow(() -> service.refresh());
        assertEquals("AK-47 | Redline (Field-Tested)", service.resolve(12345L)); // старый словарь не затёрся
    }

    @Test
    void refresh_skipsItemsWithMissingFields() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"success":true,"items":[
                          {"id":12345,"hash_name":"AK-47 | Redline (Field-Tested)"},
                          {"id":99999}
                        ]}
                        """));

        service.refresh();

        assertEquals("AK-47 | Redline (Field-Tested)", service.resolve(12345L));
        assertNull(service.resolve(99999L));
    }
}