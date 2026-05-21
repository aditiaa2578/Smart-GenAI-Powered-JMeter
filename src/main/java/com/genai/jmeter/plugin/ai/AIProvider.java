package com.genai.jmeter.plugin.ai;

import java.util.List;

/**
 * Unified interface for all AI providers: Gemini, Groq, Meta Llama.
 */
public interface AIProvider {

    /** Human-readable provider name shown in the UI. */
    String getName();

    /** Unique provider ID used in configuration. */
    String getId();

    /**
     * Sends a chat message to the AI and returns the text response.
     *
     * @param systemPrompt  Instructions/role for the AI
     * @param userMessage   The actual question or task
     * @return              The AI's text response
     */
    AIResponse chat(String systemPrompt, String userMessage) throws AIException;

    /** List of model identifiers available for this provider. */
    List<String> getAvailableModels();

    /** Returns true if the provider is properly configured with an API key. */
    boolean isConfigured();

    /** Validate the API key by making a lightweight test call. */
    boolean validateApiKey(String apiKey);

    /** Get/set the currently selected model. */
    String getSelectedModel();
    void setSelectedModel(String model);

    /** Get/set the API key. */
    String getApiKey();
    void setApiKey(String apiKey);

    /** Max tokens to request in the response. */
    default int getMaxTokens() { return 4096; }

    /** Timeout in seconds for API calls. */
    default int getTimeoutSeconds() { return 60; }
}
