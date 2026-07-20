package com.twlic.uca.client.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class TestHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, StubResponse> responses = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    public TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public void stub(String method, String path, int status, String body) {
        responses.put(method + " " + path, new StubResponse(status, body == null ? "" : body));
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
        StubResponse response = responses.getOrDefault(key, new StubResponse(404, ""));
        byte[] responseBody = response.body().getBytes(StandardCharsets.UTF_8);
        if (responseBody.length > 0) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(response.status(), responseBody.length);
            exchange.getResponseBody().write(responseBody);
        } else {
            exchange.sendResponseHeaders(response.status(), -1);
        }
        exchange.close();
    }

    public record RecordedRequest(String method, String path, String body) {
    }

    private record StubResponse(int status, String body) {
    }
}
