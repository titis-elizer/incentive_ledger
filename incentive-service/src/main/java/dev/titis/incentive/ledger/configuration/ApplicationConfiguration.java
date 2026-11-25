package dev.titis.incentive.ledger.configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ApplicationConfiguration {
    @Bean
    Clock businessClock() { return Clock.system(ZoneId.of("Asia/Jakarta")); }
    @Bean
    RestClient partnerRestClient(RestClient.Builder builder,
            @Value("${partner.base-url}") String baseUrl,
            @Value("${partner.connect-timeout}") Duration connectTimeout,
            @Value("${partner.read-timeout}") Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}