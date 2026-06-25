package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;

public class MistralClient {

    private static final String API_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(180))   // Mistral может думать долго на большом объёме постов
            .callTimeout(Duration.ofSeconds(200))
            .build();

    private final String apiKey;
    private final String model;

    public MistralClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public String summarize(String posts) throws IOException {

        String prompt = """
                Ты — помощник, который делает краткую сводку постов из Telegram-каналов за сутки.
                Сгруппируй по темам, выдели главное, убери воду и рекламу. Пиши на русском, по делу.

                Посты:
                """ + posts;

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {

            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Mistral API error " + response.code() + ": " + responseBody);
            }

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        }
    }
}
