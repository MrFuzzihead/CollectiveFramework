package com.austinv11.collectiveframework.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * A real HTTP server bound to an ephemeral loopback port, for exercising the framework's HTTP clients without
 * touching the network.
 * <p>
 * The JDK's {@code com.sun.net.httpserver} is part of the Java 8 runtime, so this needs no extra dependency.
 * Every request is recorded before the handler runs, so tests can assert on the method, path, query and headers.
 */
public final class LocalHttpServer implements AutoCloseable {

    /** A request the server received. */
    public static final class Recorded {

        public final String method;
        public final String path;
        public final String query;
        /** Header names are lower-cased. */
        public final Map<String, String> headers;

        Recorded(String method, String path, String query, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
        }
    }

    private final HttpServer server;
    private final List<Recorded> requests = new ArrayList<Recorded>();

    private LocalHttpServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            record(exchange);
            handler.handle(exchange);
        });
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "local-http-test");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
    }

    /**
     * Starts a server whose every response is produced by the given handler.
     *
     * @param handler The handler
     * @return The running server
     * @throws IOException if the port cannot be bound
     */
    public static LocalHttpServer start(HttpHandler handler) throws IOException {
        return new LocalHttpServer(handler);
    }

    /**
     * Starts a server that answers every request with the given status and UTF-8 body.
     *
     * @param status The HTTP status
     * @param body   The response body
     * @return The running server
     * @throws IOException if the port cannot be bound
     */
    public static LocalHttpServer responding(int status, String body) throws IOException {
        return start(textResponse(status, body));
    }

    /**
     * Starts a server that stalls for the given time before answering.
     *
     * @param delayMs How long to stall, in milliseconds
     * @param body    The response body
     * @return The running server
     * @throws IOException if the port cannot be bound
     */
    public static LocalHttpServer stalling(long delayMs, String body) throws IOException {
        HttpHandler responder = textResponse(200, body);
        return start(exchange -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
            }
            try {
                responder.handle(exchange);
            } catch (IOException clientGaveUp) {
                // The client timed out; nothing to do.
            }
        });
    }

    /**
     * @param status The HTTP status
     * @param body   The response body
     * @return A handler that always answers with that status and body
     */
    public static HttpHandler textResponse(int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange -> {
            exchange.getResponseHeaders()
                .add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        };
    }

    /**
     * @param path The path to request
     * @return An absolute url on this server
     */
    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress()
            .getPort() + path;
    }

    /**
     * @return The requests received so far, in order
     */
    public List<Recorded> getRequests() {
        synchronized (requests) {
            return new ArrayList<Recorded>(requests);
        }
    }

    /**
     * @return The most recent request, or null if none was received
     */
    public Recorded getLastRequest() {
        List<Recorded> all = getRequests();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    private void record(HttpExchange exchange) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders()
            .entrySet()) {
            if (entry.getKey() != null && !entry.getValue()
                .isEmpty()) {
                headers.put(
                    entry.getKey()
                        .toLowerCase(Locale.ROOT),
                    entry.getValue()
                        .get(0));
            }
        }
        Recorded recorded = new Recorded(
            exchange.getRequestMethod(),
            exchange.getRequestURI()
                .getPath(),
            // getRawQuery, not getQuery: the decoded form hides the percent-escapes we want to assert on.
            exchange.getRequestURI()
                .getRawQuery(),
            headers);
        synchronized (requests) {
            requests.add(recorded);
        }
    }

    /**
     * Reads a stream fully as UTF-8.
     *
     * @param stream The stream
     * @return The text
     * @throws IOException if the read fails
     */
    public static String readFully(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = stream.read(chunk)) != -1) buffer.write(chunk, 0, read);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
