package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Main {

    public static void main(String[] args) throws Exception {

        Database.init();

        String[] channels = {
                "teamplay21",
                 "ON_CoC",
                 "maxkiller69"
        };

        for (String channel : channels) {

            System.out.println("Читаем канал: " + channel);

            Document doc = Jsoup.connect("https://t.me/s/" + channel)
                    .userAgent("Mozilla/5.0")
                    .get();

            for (Element msg : doc.select(".tgme_widget_message_text")) {

                String text = msg.text();

                Database.savePost(
                        channel,
                        text,
                        String.valueOf(text.hashCode())
                );
            }
        }

        System.out.println("Готово");
        Database.countPosts();
        System.out.println("=== ПОСТЫ ЗА СУТКИ ===");
        System.out.println(Database.getTodayPosts());
    }
}