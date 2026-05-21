package com.genai.jmeter.plugin.ai;

import com.genai.jmeter.plugin.ai.providers.GeminiProvider;
import com.genai.jmeter.plugin.ai.providers.GroqProvider;
import com.genai.jmeter.plugin.ai.providers.MetaProvider;
import com.genai.jmeter.plugin.core.PluginConstants;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of available AI providers. Maintains singleton instances.
 */
public class AIProviderFactory {

    private static final AIProviderFactory INSTANCE = new AIProviderFactory();

    private final Map<String, AIProvider> providers = new LinkedHashMap<>();
    private String activeProviderId;

    private AIProviderFactory() {
        register(new GeminiProvider());
        register(new GroqProvider());
        register(new MetaProvider());
        activeProviderId = "groq"; // Default to Groq (free tier)
    }

    public static AIProviderFactory getInstance() { return INSTANCE; }

    private void register(AIProvider provider) {
        providers.put(provider.getId(), provider);
    }

    public AIProvider getProvider(String id) {
        AIProvider p = providers.get(id);
        if (p == null) throw new IllegalArgumentException("Unknown provider: " + id);
        return p;
    }

    public AIProvider getActiveProvider() {
        return providers.get(activeProviderId);
    }

    public void setActiveProvider(String id) {
        if (!providers.containsKey(id)) throw new IllegalArgumentException("Unknown provider: " + id);
        this.activeProviderId = id;
    }

    public String getActiveProviderId() { return activeProviderId; }

    public List<AIProvider> getAllProviders() {
        return Arrays.asList(providers.values().toArray(new AIProvider[0]));
    }

    public List<String> getProviderNames() {
        return Arrays.asList(
                PluginConstants.PROVIDER_GEMINI,
                PluginConstants.PROVIDER_GROQ,
                PluginConstants.PROVIDER_META
        );
    }

    public AIProvider getProviderByName(String name) {
        return providers.values().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the first configured provider, or null if none are configured.
     */
    public AIProvider getFirstConfiguredProvider() {
        return providers.values().stream()
                .filter(AIProvider::isConfigured)
                .findFirst()
                .orElse(null);
    }
}
