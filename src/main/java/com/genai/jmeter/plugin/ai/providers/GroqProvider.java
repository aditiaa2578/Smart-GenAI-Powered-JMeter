package com.genai.jmeter.plugin.ai.providers;

import com.genai.jmeter.plugin.ai.AIException;
import com.genai.jmeter.plugin.ai.AIProvider;
import com.genai.jmeter.plugin.ai.AIResponse;
import com.genai.jmeter.plugin.core.PluginConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Groq AI provider — free tier with ultra-fast inference.
 * Supports Meta Llama, Mixtral, and Gemma models.
 * API is OpenAI-compatible: https://api.groq.com/openai/v1/chat/completions
 */
public class GroqProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(GroqProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Updated list from https://console.groq.com/docs/models (production + preview)
    private static final List<String> MODELS = Arrays.asList(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "qwen/qwen3-32b",
            "groq/compound",
            "groq/compound-mini"
    );

    private String apiKey = "";
    private String selectedModel = PluginConstants.GROQ_DEFAULT_MODEL;
    private OkHttpClient client;

    public GroqProvider() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() { return PluginConstants.PROVIDER_GROQ; }

    @Override
    public String getId() { return "groq"; }

    @Override
    public AIResponse chat(String systemPrompt, String userMessage) throws AIException {
        if (!isConfigured()) throw new AIException(getName(), "Groq API key is not configured");

        JsonObject body = buildRequestBody(systemPrompt, userMessage);
        long start = System.currentTimeMillis();

        try {
            Request request = new Request.Builder()
                    .url(PluginConstants.GROQ_BASE_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - start;
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    String errMsg = extractErrorMessage(responseBody);
                    throw new AIException(getName(), response.code(),
                            "Groq API error " + response.code() + ": " + errMsg);
                }

                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                String text = json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();

                int inputTokens = 0, outputTokens = 0;
                if (json.has("usage")) {
                    JsonObject usage = json.getAsJsonObject("usage");
                    inputTokens = usage.get("prompt_tokens").getAsInt();
                    outputTokens = usage.get("completion_tokens").getAsInt();
                }

                return new AIResponse.Builder()
                        .text(text).provider(getName()).model(selectedModel)
                        .latencyMs(latency).inputTokens(inputTokens).outputTokens(outputTokens)
                        .success(true).build();
            }
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException(getName(), "Network error calling Groq API: " + e.getMessage(), e);
        }
    }

    private JsonObject buildRequestBody(String systemPrompt, String userMessage) {
        JsonObject body = new JsonObject();
        body.addProperty("model", selectedModel);
        body.addProperty("max_tokens", getMaxTokens());
        body.addProperty("temperature", 0.3);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", systemPrompt);
            messages.add(sys);
        }
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userMessage);
        messages.add(user);

        body.add("messages", messages);
        return body;
    }

    private String extractErrorMessage(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error")) {
                JsonObject err = json.getAsJsonObject("error");
                return err.has("message") ? err.get("message").getAsString() : body;
            }
        } catch (Exception ignored) {}
        return body;
    }

    @Override
    public List<String> getAvailableModels() { return MODELS; }

    @Override
    public boolean isConfigured() { return apiKey != null && !apiKey.trim().isEmpty(); }

    @Override
    public boolean validateApiKey(String apiKey) {
        String saved = this.apiKey;
        this.apiKey = apiKey;
        try {
            AIResponse r = chat("", "Reply with one word: OK");
            return r.isSuccess();
        } catch (Exception e) {
            log.warn("Groq API key validation failed: {}", e.getMessage());
            return false;
        } finally {
            this.apiKey = saved;
        }
    }

    @Override
    public String getSelectedModel() { return selectedModel; }
    @Override
    public void setSelectedModel(String model) { this.selectedModel = model; }
    @Override
    public String getApiKey() { return apiKey; }
    @Override
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
