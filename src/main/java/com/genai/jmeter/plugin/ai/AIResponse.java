package com.genai.jmeter.plugin.ai;

/**
 * Wraps the response from any AI provider with metadata.
 */
public class AIResponse {

    private final String text;
    private final String model;
    private final String provider;
    private final int inputTokens;
    private final int outputTokens;
    private final long latencyMs;
    private final boolean success;
    private final String errorMessage;

    private AIResponse(Builder b) {
        this.text = b.text;
        this.model = b.model;
        this.provider = b.provider;
        this.inputTokens = b.inputTokens;
        this.outputTokens = b.outputTokens;
        this.latencyMs = b.latencyMs;
        this.success = b.success;
        this.errorMessage = b.errorMessage;
    }

    public static AIResponse success(String text, String provider, String model, long latencyMs) {
        return new Builder().text(text).provider(provider).model(model)
                .latencyMs(latencyMs).success(true).build();
    }

    public static AIResponse error(String errorMessage, String provider) {
        return new Builder().errorMessage(errorMessage).provider(provider).success(false).build();
    }

    public String getText() { return text; }
    public String getModel() { return model; }
    public String getProvider() { return provider; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public long getLatencyMs() { return latencyMs; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        return success ? String.format("[%s/%s] %dms: %s...",
                provider, model, latencyMs,
                text != null && text.length() > 60 ? text.substring(0, 60) : text)
                : "[ERROR] " + errorMessage;
    }

    public static class Builder {
        private String text, model, provider, errorMessage;
        private int inputTokens, outputTokens;
        private long latencyMs;
        private boolean success;

        public Builder text(String v) { this.text = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder provider(String v) { this.provider = v; return this; }
        public Builder inputTokens(int v) { this.inputTokens = v; return this; }
        public Builder outputTokens(int v) { this.outputTokens = v; return this; }
        public Builder latencyMs(long v) { this.latencyMs = v; return this; }
        public Builder success(boolean v) { this.success = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }
        public AIResponse build() { return new AIResponse(this); }
    }
}
