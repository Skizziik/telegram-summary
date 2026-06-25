package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final String[] CHANNELS = {
            "teamplay21",
            "ON_CoC",
            "maxkiller69"
    };

    private static final LocalTime RUN_AT = LocalTime.of(23, 0);

    public static void main(String[] args) throws Exception {

        Database.init();

        // Прогон "сейчас" для теста:  java -jar telegram-summary.jar now
        if (args.length > 0 && args[0].equalsIgnoreCase("now")) {
            runPipeline();
            return;
        }

        // Иначе — демон с планировщиком на 23:00 каждый день.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduleNextRun(scheduler);
        System.out.println("Планировщик запущен. Сводка каждый день в " + RUN_AT + ".");
    }

    private static void scheduleNextRun(ScheduledExecutorService scheduler) {
        long delay = secondsUntilNext(RUN_AT);
        scheduler.schedule(() -> {
            try {
                runPipeline();
            } catch (Exception e) {
                System.err.println("Ошибка пайплайна: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Перепланируем на следующий день в любом случае.
                scheduleNextRun(scheduler);
            }
        }, delay, TimeUnit.SECONDS);
    }

    private static long secondsUntilNext(LocalTime time) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(time);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).getSeconds();
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
