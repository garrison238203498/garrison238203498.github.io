import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class PoisonAirlockWebServer {
    private static final int PORT = 8080;
    private static final Path WEB_ROOT = Path.of("web");

    private Game game;

    public PoisonAirlockWebServer() {
        game = new Game();
        game.begin();
    }

    public static void main(String[] args) throws IOException {
        new PoisonAirlockWebServer().start();
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/state", new StateHandler());
        server.createContext("/api/input", new InputHandler());
        server.createContext("/api/reset", new ResetHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Poison Airlock Escape web UI running at http://localhost:" + PORT);
        System.out.println("Use PoisonAirlockEscape for the original console version.");
    }

    private class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendPlain(exchange, 405, "Method Not Allowed");
                return;
            }

            sendJson(exchange, 200, game.toWebStateJson());
        }
    }

    private class InputHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendPlain(exchange, 405, "Method Not Allowed");
                return;
            }

            String input = readBody(exchange.getRequestBody());
            game.submitInput(input);
            sendJson(exchange, 200, game.toWebStateJson());
        }
    }

    private class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendPlain(exchange, 405, "Method Not Allowed");
                return;
            }

            game = new Game();
            game.begin();
            sendJson(exchange, 200, game.toWebStateJson());
        }
    }

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            Path filePath = resolveStaticPath(requestPath);

            if (filePath == null || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendPlain(exchange, 404, "Not Found");
                return;
            }

            byte[] body = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType(filePath));
            exchange.sendResponseHeaders(200, body.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }

    private Path resolveStaticPath(String requestPath) {
        String normalized = requestPath == null || requestPath.equals("/") ? "/index.html" : requestPath;
        String relative = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        Path resolved = WEB_ROOT.resolve(relative).normalize();

        if (!resolved.startsWith(WEB_ROOT)) {
            return null;
        }

        return resolved;
    }

    private String contentType(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }

        return "application/octet-stream";
    }

    private String readBody(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void sendPlain(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
