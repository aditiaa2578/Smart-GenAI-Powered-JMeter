package com.genai.jmeter.plugin.listener;

import com.genai.jmeter.plugin.ai.AIProvider;
import com.genai.jmeter.plugin.ai.AIProviderFactory;
import com.genai.jmeter.plugin.ai.AIResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyses live results from a test run and suggests correlations using AI.
 */
public class LiveCorrelationEngine {

    private static final Logger log = LoggerFactory.getLogger(LiveCorrelationEngine.class);

    private static final String SYSTEM_PROMPT =
        "You are a JMeter correlation expert. Analyse HTTP response bodies and identify " +
        "dynamic values that need to be extracted for subsequent requests. " +
        "Look for: session tokens, CSRF tokens, auth tokens, dynamic IDs, JWT tokens, cookies.\n\n" +
        "Output ONLY valid JSON (no markdown, no prose):\n" +
        "{\"extractors\": [{\"variable\": \"str\", \"type\": \"REGEX|JSON_PATH|BOUNDARY\", " +
        "\"expression\": \"str\", \"reasoning\": \"str\", \"confidence\": \"HIGH|MEDIUM|LOW\"}]}";

    private final AIProvider aiProvider;
    private Consumer<String> statusCallback;

    public LiveCorrelationEngine() {
        this.aiProvider = AIProviderFactory.getInstance().getActiveProvider();
    }

    public void setStatusCallback(Consumer<String> cb) { this.statusCallback = cb; }

    /**
     * Analyses a single result entry and populates it with extractor hints.
     */
    public void analyseEntry(ResultEntry entry) {
        String body = entry.getResponseBody();
        if (body == null || body.trim().isEmpty()) return;

        // Rule-based quick pass first (fast, no AI)
        applyRules(entry, body);

        // AI analysis for deeper patterns
        if (aiProvider != null && aiProvider.isConfigured()) {
            aiAnalyse(entry, body);
        }
    }

    /**
     * Analyses all entries together to find cross-request dynamic values.
     */
    public void analyseAll(List<ResultEntry> entries) {
        emit("Analysing " + entries.size() + " results for correlation opportunities...");

        if (aiProvider == null || !aiProvider.isConfigured()) {
            emit("No AI provider configured — running rule-based analysis only");
            entries.forEach(e -> applyRules(e, e.getResponseBody()));
            return;
        }

        // Build a concise summary of all responses for batch AI analysis
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyse these HTTP responses and identify values that need correlation.\n\n");

        for (ResultEntry entry : entries) {
            String body = entry.getResponseBody();
            if (body == null || body.length() < 5) continue;
            prompt.append("=== Sampler ").append(entry.getIndex())
                    .append(": ").append(entry.getSamplerName()).append(" ===\n");
            prompt.append("URL: ").append(entry.getUrl()).append("\n");
            prompt.append("Status: ").append(entry.getStatusCode()).append("\n");
            String preview = body.length() > 500 ? body.substring(0, 500) + "..." : body;
            prompt.append("Response: ").append(preview).append("\n\n");
        }
        prompt.append("Return extractors array. For samplerIndex field use the Sampler # above.");

        try {
            AIResponse response = aiProvider.chat(SYSTEM_PROMPT, prompt.toString());
            if (response.isSuccess()) {
                parseAndApply(response.getText(), entries);
                emit("AI analysis complete — found extractors across " + entries.size() + " results");
            }
        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage(), e);
            emit("AI analysis failed: " + e.getMessage());
        }
    }

    private void applyRules(ResultEntry entry, String body) {
        if (body == null) return;

        // JWT detection
        Pattern jwt = Pattern.compile("\"[a-zA-Z_]*[tT]oken[a-zA-Z_]*\"\\s*:\\s*\"(ey[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]*)\"");
        Matcher m = jwt.matcher(body);
        if (m.find()) {
            String field = m.group(0).split("\"")[1];
            entry.addExtractorHint(new ResultEntry.ExtractorHint(
                    toVarName(field), ResultEntry.ExtractorHint.Type.JSON_PATH,
                    "$." + field, "JWT token detected in JSON response"));
        }

        // CSRF in HTML hidden input
        Pattern csrf = Pattern.compile("name=\"([^\"]*(?:csrf|token|xsrf)[^\"]*)\"\\s+value=\"([^\"]{8,})\"", Pattern.CASE_INSENSITIVE);
        m = csrf.matcher(body);
        if (m.find()) {
            entry.addExtractorHint(new ResultEntry.ExtractorHint(
                    toVarName(m.group(1)), ResultEntry.ExtractorHint.Type.REGEX,
                    "name=\"" + Pattern.quote(m.group(1)) + "\"\\s+value=\"([^\"]+)\"",
                    "CSRF/hidden input field"));
        }

        // Generic access_token / refresh_token in JSON
        Pattern tokenField = Pattern.compile("\"(access_token|refresh_token|id_token|auth_token)\"\\s*:\\s*\"([^\"]{8,})\"");
        m = tokenField.matcher(body);
        while (m.find()) {
            entry.addExtractorHint(new ResultEntry.ExtractorHint(
                    toVarName(m.group(1)), ResultEntry.ExtractorHint.Type.JSON_PATH,
                    "$." + m.group(1), "OAuth/auth token field"));
        }
    }

    private void aiAnalyse(ResultEntry entry, String body) {
        try {
            String prompt = "Sampler: " + entry.getSamplerName() + "\nURL: " + entry.getUrl() +
                    "\nStatus: " + entry.getStatusCode() + "\nResponse (first 800 chars):\n" +
                    (body.length() > 800 ? body.substring(0, 800) : body);

            AIResponse response = aiProvider.chat(SYSTEM_PROMPT, prompt);
            if (response.isSuccess()) {
                parseExtractors(response.getText()).forEach(entry::addExtractorHint);
            }
        } catch (Exception e) {
            log.debug("AI single-entry analysis failed: {}", e.getMessage());
        }
    }

    private void parseAndApply(String text, List<ResultEntry> entries) {
        try {
            String json = extractJson(text);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("extractors")) return;

            for (JsonElement el : root.getAsJsonArray("extractors")) {
                JsonObject ext = el.getAsJsonObject();
                int samplerIdx = ext.has("samplerIndex") ? ext.get("samplerIndex").getAsInt() : 0;
                ResultEntry target = samplerIdx < entries.size() ? entries.get(samplerIdx) : entries.get(0);

                ResultEntry.ExtractorHint.Type type;
                try {
                    type = ResultEntry.ExtractorHint.Type.valueOf(
                            ext.has("type") ? ext.get("type").getAsString().toUpperCase() : "REGEX");
                } catch (Exception e) { type = ResultEntry.ExtractorHint.Type.REGEX; }

                target.addExtractorHint(new ResultEntry.ExtractorHint(
                        ext.has("variable") ? ext.get("variable").getAsString() : "var",
                        type,
                        ext.has("expression") ? ext.get("expression").getAsString() : "",
                        ext.has("reasoning") ? ext.get("reasoning").getAsString() : ""));
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI extractors: {}", e.getMessage());
        }
    }

    private java.util.List<ResultEntry.ExtractorHint> parseExtractors(String text) {
        java.util.List<ResultEntry.ExtractorHint> hints = new java.util.ArrayList<>();
        try {
            String json = extractJson(text);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("extractors")) return hints;
            for (JsonElement el : root.getAsJsonArray("extractors")) {
                JsonObject ext = el.getAsJsonObject();
                ResultEntry.ExtractorHint.Type type;
                try {
                    type = ResultEntry.ExtractorHint.Type.valueOf(
                            ext.has("type") ? ext.get("type").getAsString().toUpperCase() : "REGEX");
                } catch (Exception e) { type = ResultEntry.ExtractorHint.Type.REGEX; }
                hints.add(new ResultEntry.ExtractorHint(
                        str(ext, "variable", "var"), type,
                        str(ext, "expression", ""), str(ext, "reasoning", "")));
            }
        } catch (Exception ignored) {}
        return hints;
    }

    /**
     * Auto-generates a regex from selected text with left/right boundary detection.
     */
    public static String generateRegex(String responseBody, String selectedText) {
        if (selectedText == null || selectedText.isEmpty()) return "";
        int pos = responseBody.indexOf(selectedText);
        if (pos < 0) return Pattern.quote(selectedText);

        // Look for up to 20 chars of context on each side
        int leftStart = Math.max(0, pos - 20);
        int rightEnd = Math.min(responseBody.length(), pos + selectedText.length() + 20);
        String leftCtx = responseBody.substring(leftStart, pos);
        String rightCtx = responseBody.substring(pos + selectedText.length(), rightEnd);

        // Find natural boundary chars (quotes, colons, brackets, newlines)
        String leftBound = findRightBoundary(leftCtx);
        String rightBound = findLeftBoundary(rightCtx);

        if (!leftBound.isEmpty() && !rightBound.isEmpty()) {
            return Pattern.quote(leftBound) + "([^" + Pattern.quote(String.valueOf(rightBound.charAt(0))) + "]+)" + Pattern.quote(rightBound);
        }
        return "([^&\"'<>\\s]+)";
    }

    private static String findRightBoundary(String ctx) {
        String[] delimiters = {"\"", "'", ":", "=", "(", "[", "{"};
        for (int i = ctx.length() - 1; i >= 0; i--) {
            for (String d : delimiters) if (d.equals(String.valueOf(ctx.charAt(i)))) return String.valueOf(ctx.charAt(i));
        }
        return "";
    }

    private static String findLeftBoundary(String ctx) {
        String[] delimiters = {"\"", "'", "&", ")", "]", "}", "<", " ", "\n"};
        for (int i = 0; i < ctx.length(); i++) {
            for (String d : delimiters) if (d.equals(String.valueOf(ctx.charAt(i)))) return String.valueOf(ctx.charAt(i));
        }
        return "";
    }

    private String extractJson(String text) {
        int s = text.indexOf('{'), e = text.lastIndexOf('}');
        return (s >= 0 && e > s) ? text.substring(s, e + 1) : text;
    }

    private String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private String toVarName(String s) {
        return s.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    private void emit(String msg) {
        if (statusCallback != null) statusCallback.accept(msg);
        log.info(msg);
    }
}
