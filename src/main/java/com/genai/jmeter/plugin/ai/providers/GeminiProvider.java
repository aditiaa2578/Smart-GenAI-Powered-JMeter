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
 * Google Gemini AI provider.
 * Uses the v1beta REST endpoint:
 *   https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
 *
 * Request schema:
 *   {
 *     "systemInstruction": { "parts": [{"text": "..."}] },
 *     "contents": [ {"role":"user","parts":[{"text":"..."}]} ],
 *     "generationConfig": { "maxOutputTokens": N, "temperature": 0.3 }
 *   }
 */
public class GeminiProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Current production-available models (Nov 2025+). Older 1.5/2.0 lineup retired.
    private static final List<String> MODELS = Arrays.asList(
            "gemini-2.5-flash",          // default — fastest production
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro",
            "gemini-flash-latest",
            "gemini-pro-latest"
    );

    private String apiKey = "";
    private String selectedModel = PluginConstants.GEMINI_DEFAULT_MODEL;
    private final OkHttpClient client;

    public GeminiProvider() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() { return PluginConstants.PROVIDER_GEMINI; }

    @Override
    public String getId() { return "gemini"; }

    @Override
    public AIResponse chat(String systemPrompt, String userMessage) throws AIException {
        if (!isConfigured()) throw new AIException(getName(), "Gemini API key is not configured");

        String url = PluginConstants.GEMINI_BASE_URL + selectedModel + ":generateContent?key=" + apiKey;

        JsonObject body = new JsonObject();

        // System instruction as a top-level field (current v1beta format)
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sysInstr = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", systemPrompt);
            sysParts.add(sysPart);
            sysInstr.add("parts", sysParts);
            body.add("systemInstruction", sysInstr);
        }

        // User message
        JsonArray contents = new JsonArray();
        JsonObject userObj = new JsonObject();
        JsonArray userParts = new JsonArray();
        JsonObject userPart = new JsonObject();
        userPart.addProperty("text", userMessage);
        userParts.add(userPart);
        userObj.addProperty("role", "user");
        userObj.add("parts", userParts);
        contents.add(userObj);
        body.add("contents", contents);

        // Generation config
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", getMaxTokens());
        genConfig.addProperty("temperature", 0.3);
        body.add("generationConfig", genConfig);

        long start = System.currentTimeMillis();
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - start;
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    String errMsg = extractErrorMessage(responseBody);
                    throw new AIException(getName(), response.code(),
                            "Gemini API error " + response.code() + ": " + errMsg);
                }

                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                if (!json.has("candidates") || json.getAsJsonArray("candidates").isEmpty()) {
                    // Response was blocked or empty
                    String reason = "no candidates in response";
                    if (json.has("promptFeedback")) {
                        reason = "blocked: " + json.getAsJsonObject("promptFeedback").toString();
                    }
                    throw new AIException(getName(), 200, "Gemini returned empty response (" + reason + ")");
                }

                JsonObject candidate = json.getAsJsonArray("candidates").get(0).getAsJsonObject();
                if (!candidate.has("content")) {
                    throw new AIException(getName(), 200,
                            "Gemini returned no content; finishReason="
                                    + (candidate.has("finishReason") ? candidate.get("finishReason").getAsString() : "unknown"));
                }

                JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < parts.size(); i++) {
                    JsonObject part = parts.get(i).getAsJsonObject();
                    if (part.has("text")) text.append(part.get("text").getAsString());
                }

                return AIResponse.success(text.toString(), getName(), selectedModel, latency);
            }
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException(getName(), "Network error calling Gemini API: " + e.getMessage(), e);
        }
    }

    private String extractErrorMessage(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error")) {
                JsonObject err = json.getAsJsonObject("error");
                if (err.has("message")) return err.get("message").getAsString();
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
            AIResponse r = chat("", "Reply with exactly: OK");
            return r.isSuccess();
        } catch (Exception e) {
            log.warn("Gemini API key validation failed: {}", e.getMessage());
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
