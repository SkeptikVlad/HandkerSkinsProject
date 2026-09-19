package org.example.handkerskinsproject.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(4));

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    /**
     * Отдельный пул для покупки — виртуальные потоки, чтобы блокирующий
     * HTTP-вызов /buy никогда не задерживал чтение WebSocket-сообщений.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService buyExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}