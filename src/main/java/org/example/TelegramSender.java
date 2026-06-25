package org.example;

import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TelegramSender {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Лимит Telegram — 4096 символов на сообщение, берём с запасом.
    private static final int MAX_LEN = 4000;

    private final OkHttpClient client = new OkHttpClient();

    private final String botToken;
    private final String chatId;

    public TelegramSender(String botToken, String chatId) {
        this.botToken = botToken;
        this.chatId = chatId;
    }

    public void send(String text) throws IOException {
        for (String chunk : split(text)) {
            sendChunk(chunk);
        }
    }

    private void sendChunk(String text) throws IOException {

        JsonObject body = new JsonObject();
        body.addProperty("chat_id", chatId);
        body.addProperty("text", text);

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + botToken + "/sendMessage")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("Telegram API error " + response.code() + ": " + err);
            }
        }
    }

    private static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += MAX_LEN) {
            parts.add(text.substring(i, Math.min(text.length(), i + MAX_LEN)));
        }
        if (parts.isEmpty()) {
            parts.add("(пусто)");
        }
        return parts;
    }
}
