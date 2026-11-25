// package com.webhook.Webhook.config;

// import io.netty.channel.ChannelOption;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.http.client.reactive.ReactorClientHttpConnector;
// import org.springframework.web.reactive.function.client.WebClient;
// import reactor.netty.http.client.HttpClient;

// import java.time.Duration;

// @Configuration
// public class WebClientConfig {

//     @Value("${webhook.worker.response-timeout-seconds:5}")
//     private int responseTimeoutSeconds;

//     @Bean
//     public WebClient webhookWebClient() {
//         HttpClient httpClient = HttpClient.create()
//                 .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
//                 .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds));

//         return WebClient.builder()
//                 .clientConnector(new ReactorClientHttpConnector(httpClient))
//                 .build();
//     }
// }



package com.webhook.Webhook.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Value("${webhook.worker.connect-timeout-millis:3000}")
    private int connectTimeoutMillis;

    @Value("${webhook.worker.response-timeout-seconds:5}")
    private int responseTimeoutSeconds;

    @Bean
    public WebClient webhookWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(responseTimeoutSeconds, TimeUnit.SECONDS)
                ));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "Distributed-Webhook-Dispatcher/1.0")
                .build();
    }
}