package br.com.nevvesdev.alcada.infrastructure.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class StubHttpServer implements AutoCloseable {

    public record RecordedRequest(String method, String path, Map<String, List<String>> headers, String body) {

        public String header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    public record Response(int status, String body, Duration delay) {

        public static Response empty(int status) {
            return new Response(status, "", Duration.ZERO);
        }

        public static Response json(int status, String body) {
            return new Response(status, body, Duration.ZERO);
        }

        public static Response delayed(int status, String body, Duration delay) {
            return new Response(status, body, delay);
        }
    }

    @FunctionalInterface
    public interface Route {

        Response respond(RecordedRequest request, int callNumber);
    }

    private record RegisteredRoute(Route route, AtomicInteger calls) {
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final Map<String, RegisteredRoute> routes = new ConcurrentHashMap<>();

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::dispatch);
        server.setExecutor(executor);
        server.start();
    }

    public StubHttpServer on(String method, String path, Route route) {
        routes.put(key(method, path), new RegisteredRoute(route, new AtomicInteger()));
        return this;
    }

    public StubHttpServer respond(String method, String path, Response response) {
        return on(method, path, (request, callNumber) -> response);
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public int requestCount() {
        return requests.size();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        RecordedRequest request = record(exchange);
        requests.add(request);
        RegisteredRoute registered = routes.get(key(request.method(), request.path()));
        Response response = registered == null
                ? Response.empty(404)
                : registered.route().respond(request, registered.calls().incrementAndGet());
        pause(response.delay());
        write(exchange, response);
    }

    private static RecordedRequest record(HttpExchange exchange) throws IOException {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return new RecordedRequest(
                exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body);
    }

    private static void pause(Duration delay) {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void write(HttpExchange exchange, Response response) {
        try {
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (bytes.length == 0) {
                exchange.sendResponseHeaders(response.status(), -1);
            } else {
                exchange.sendResponseHeaders(response.status(), bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            }
        } catch (IOException ignored) {
        } finally {
            exchange.close();
        }
    }

    private static String key(String method, String path) {
        return method + " " + path;
    }
}