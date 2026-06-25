package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    private static final String[] CHANNELS = {
            "teamplay21",
            "ON_CoC",
            "maxkiller69"
    };

    // Защита от параллельных запусков (cron + ручной триггер одновременно).
    private static final AtomicBoolean running = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {

        Database.init();

        // Разовый прогон для теста:  java -jar telegram-summary.jar now
        if (args.length > 0 && args[0].equalsIgnoreCase("now")) {
            runPipeline();
            return;
        }

        // Иначе — поднимаем HTTP-сервер, который дёргает внешний крон (cron-job.org).
        startServer();
    }

    private static void startServer() throws IOException {
        int port = Integer.parseInt(envOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/run", Main::handleRun);
        server.createContext("/", exchange -> respond(exchange, 200, "ok"));

        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();
        System.out.println("HTTP-сервер запущен на порту " + port + ". Эндпоинт: /run?token=...");
    }

    private static void handleRun(HttpExchange exchange) throws IOException {

        // Проверка секретного токена из ?token=...
        String expected = System.getenv("RUN_TOKEN");
        String got = queryParam(exchange, "token");
        if (expected == null || expected.isBlank() || !expected.equals(got)) {
            respond(exchange, 403, "forbidden");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            respond(exchange, 409, "already running");
            return;
        }

        // Запускаем пайплайн в фоне и сразу отвечаем 200 — чтобы крон не словил
        // таймаут на холодном старте Render.
        new Thread(() -> {
            try {
                runPipeline();
            } catch (Exception e) {
                System.err.println("Ошибка пайплайна: " + e.getMessage());
                e.printStackTrace();
            } finally {
                running.set(false);
            }
        }, "pipeline").start();

        respond(exchange, 200, "pipeline started");
    }

    private static void runPipeline() throws Exception {

        System.out.println("=== Старт пайплайна " + LocalDateTime.now() + " ===");

        // 1. Скрейпим каналы → БД.
        scrapeChannels();

        // 2. Берём посты за сутки.
        String posts = Database.getTodayPosts();
        if (posts.isBlank()) {
            System.out.println("Постов за сутки нет — сводка не нужна.");
            return;
        }

        // 3. Отправляем в Mistral, получаем сводку.
        String apiKey = requireEnv("MISTRAL_API_KEY");
        String model = envOrDefault("MISTRAL_MODEL", "mistral-small-latest");
        String summary = new MistralClient(apiKey, model).summarize(posts);
        System.out.println("=== СВОДКА ===\n" + summary);

        // 4. Отправляем сводку в Telegram.
        String botToken = requireEnv("TELEGRAM_BOT_TOKEN");
        String chatId = requireEnv("TELEGRAM_CHAT_ID");
        new TelegramSender(botToken, chatId).send("📋 Сводка за сутки:\n\n" + summary);

        System.out.println("=== Готово, сводка отправлена ===");
    }

    private static void scrapeChannels() throws Exception {
        for (String channel : CHANNELS) {

            System.out.println("Читаем канал: " + channel);

            Document doc = Jsoup.connect("https://t.me/s/" + channel)
                    .userAgent("Mozilla/5.0")
                    .get();

            for (Element msg : doc.select(".tgme_widget_message_text")) {
                String text = msg.text();
                Database.savePost(channel, text, String.valueOf(text.hashCode()));
            }
        }
        Database.countPosts();
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        return Arrays.stream(query.split("&"))
                .map(p -> p.split("=", 2))
                .filter(kv -> kv.length == 2 && kv[0].equals(name))
                .map(kv -> kv[1])
                .findFirst()
                .orElse(null);
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Не задана переменная окружения: " + name);
        }
        return value;
    }

    private static String envOrDefault(String name, String def) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? def : value;
    }
}
