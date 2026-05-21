package com.genai.jmeter.plugin.core;

public final class PluginConstants {

    private PluginConstants() {}

    public static final String PLUGIN_NAME = "GenAI Correlation Plugin";
    public static final String PLUGIN_VERSION = "1.0.0";

    // AI Provider names
    public static final String PROVIDER_GEMINI = "Gemini";
    public static final String PROVIDER_GROQ = "Groq";
    public static final String PROVIDER_META = "Meta (Llama)";

    // Gemini
    public static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    public static final String GEMINI_DEFAULT_MODEL = "gemini-2.5-flash";

    // Groq (free tier)
    public static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1/chat/completions";
    public static final String GROQ_DEFAULT_MODEL = "llama-3.3-70b-versatile";

    // Meta Llama API
    public static final String META_BASE_URL = "https://api.llama.com/v1/chat/completions";
    public static final String META_DEFAULT_MODEL = "Llama-4-Scout-17B-16E-Instruct";

    // Correlation extractor types
    public static final String EXTRACTOR_REGEX = "RegexExtractor";
    public static final String EXTRACTOR_JSONPATH = "JSONPathExtractor";
    public static final String EXTRACTOR_GROOVY = "JSR223PostProcessor";
    public static final String EXTRACTOR_XPATH = "XPathExtractor";

    // Token patterns
    public static final String PATTERN_JWT = "JWT";
    public static final String PATTERN_UUID = "UUID";
    public static final String PATTERN_BASE64 = "Base64";
    public static final String PATTERN_CSRF = "CSRF";
    public static final String PATTERN_SESSION = "Session";

    // Quality thresholds
    public static final double QUALITY_PASS = 0.85;
    public static final double QUALITY_WARN = 0.65;

    // Config keys
    public static final String CONFIG_AI_PROVIDER = "ai.provider";
    public static final String CONFIG_AI_API_KEY = "ai.apikey";
    public static final String CONFIG_AI_MODEL = "ai.model";
    public static final String CONFIG_AI_TIMEOUT = "ai.timeout.seconds";
}
