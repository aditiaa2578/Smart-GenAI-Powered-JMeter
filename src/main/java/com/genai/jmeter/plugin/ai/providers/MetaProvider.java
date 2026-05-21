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
 * Meta Llama API provider.
 * Uses Meta's official Llama API: https://api.llama.com/v1/chat/completions
 * OpenAI-compatible endpoint. Get access at: https://llama.meta.com/
 *
 * Also supports fallback to Together AI (another free-tier Llama host)
 * by changing the base URL to: https://api.together.xyz/v1/chat/completions
 */
public class MetaProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(MetaProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String META_LLAMA_API = "https://api.llama.com/v1/chat/completions";
    private static final String TOGETHER_AI_API = "https://api.together.xyz/v1/chat/completions";

    // Meta Llama models (official API IDs)
    private static final List<String> META_MODELS = Arrays.asList(
            "Llama-4-Scout-17B-16E-Instruct",
            "Llama-4-Maverick-17B-128E-Instruct-FP8",
            "Meta-Llama-3.3-70B-Instruct",
            "Meta-Llama-3.1-405B-Instruct",
            "Meta-Llama-3.1-70B-Instruct",
            "Meta-Llama-3.1-8B-Instruct"
    );

    // Together AI model IDs for Llama (fallback)
    private static final List<String> TOGETHER_MODELS = Arrays.asList(
            "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo",
            "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo",
            "meta-llama/Meta-Llama-3.3-70B-Instruct-Turbo",
            "meta-llama/Llama-3-70b-chat-hf"
    );

    public enum Backend { META_OFFICIAL, TOGETHER_AI }

    private String apiKey = "";
    private String selectedModel = PluginConstants.META_DEFAULT_MODEL;
    private Backend backend = Backend.META_OFFICIAL;
    private OkHttpClient client;

    public MetaProvider() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() { return PluginConstants.PROVIDER_META; }

    @Override
    public String getId() { return "meta"; }

    @Override
    public AIResponse chat(String systemPrompt, String userMessage) throws AIException {
        if (!isConfigured()) throw new AIException(getName(), "Meta API key is not configured");

        String endpoint = backend == Backend.TOGETHER_AI ? TOGETHER_AI_API : META_LLAMA_API;
        JsonObject body = buildRequestBody(systemPrompt, userMessage);
        long start = System.currentTimeMillis();

        try {
            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - start;
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    throw new AIException(getName(), response.code(),
                            "Meta API error " + response.code() + ": " + responseBody);
                }

                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                String text = json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();

                int inputTokens = 0, outputTokens = 0;
                if (json.has("usage")) {
                    JsonObject usage = json.getAsJsonObject("usage");
                    if (usage.has("prompt_tokens")) inputTokens = usage.get("prompt_tokens").getAsInt();
                    if (usage.has("completion_tokens")) outputTokens = usage.get("completion_tokens").getAsInt();
                }

                return new AIResponse.Builder()
                        .text(text).provider(getName()).model(selectedModel)
                        .latencyMs(latency).inputTokens(inputTokens).outputTokens(outputTokens)
                        .success(true).build();
            }
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException(getName(), "Network error calling Meta API: " + e.getMessage(), e);
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

    public void setBackend(Backend backend) {
        this.backend = backend;
        // Switch to matching default model
        this.selectedModel = backend == Backend.TOGETHER_AI
                ? "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo"
                : PluginConstants.META_DEFAULT_MODEL;
    }

    public Backend getBackend() { return backend; }

    @Override
    public List<String> getAvailableModels() {
        return backend == Backend.TOGETHER_AI ? TOGETHER_MODELS : META_MODELS;
    }

    @Override
    public boolean isConfigured() { return apiKey != null && !apiKey.trim().isEmpty(); }

    @Override
    public boolean validateApiKey(String apiKey) {
        String saved = this.apiKey;
        this.apiKey = apiKey;
        try {
            AIResponse r = chat("You are a helpful assistant.", "Say: OK");
            return r.isSuccess();
        } catch (Exception e) {
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
