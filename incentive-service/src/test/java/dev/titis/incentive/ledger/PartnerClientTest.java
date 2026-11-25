package dev.titis.incentive.ledger;

import com.sun.net.httpserver.HttpServer;
import dev.titis.incentive.ledger.client.PartnerClient;
import dev.titis.incentive.ledger.exception.BusinessException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;

class PartnerClientTest {
    @Test void mapsHttpFailuresAndValidatesSuccessContract() throws Exception {
        var id = UUID.randomUUID();
        assertResponse(id, 200, "{\"id\":\"" + id + "\",\"code\":\"AGT-001\",\"status\":\"ACTIVE\"}", 0);
        assertResponse(id, 200, "{\"id\":\"" + id + "\",\"code\":\"AGT-001\",\"status\":\"INACTIVE\"}", 422);
        assertResponse(id, 404, "{}", 422);
        assertResponse(id, 500, "{}", 503);
        assertResponse(id, 403, "{}", 502);
        assertResponse(id, 200, "{broken", 502);
        assertResponse(id, 200, "{}", 502);
        assertResponse(id, 200, "{\"id\":\"" + UUID.randomUUID() + "\",\"code\":\"AGT-001\",\"status\":\"ACTIVE\"}", 502);
    }
    @Test void mapsReadTimeoutWithoutExternalNetwork() throws Exception {
        var release = new CountDownLatch(1);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> client(server, 100).requireActive(UUID.randomUUID()))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
        } finally { release.countDown(); server.stop(0); executor.close(); }
    }
    private void assertResponse(UUID id, int status, String body, int expected) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, data.length);
            try (var out = exchange.getResponseBody()) { out.write(data); }
        });
        server.start();
        try {
            if (expected == 0) assertThat(client(server, 1000).requireActive(id).id()).isEqualTo(id);
            else assertThatThrownBy(() -> client(server, 1000).requireActive(id))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(expected));
        } finally { server.stop(0); }
    }
    private PartnerClient client(HttpServer server, int timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofMillis(timeout));
        return new PartnerClient(RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestFactory(factory).build());
    }
}