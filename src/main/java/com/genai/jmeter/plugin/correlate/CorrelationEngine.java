package com.genai.jmeter.plugin.correlate;

import com.genai.jmeter.plugin.ai.AIProvider;
import com.genai.jmeter.plugin.ai.AIProviderFactory;
import com.genai.jmeter.plugin.ai.AIResponse;
import com.genai.jmeter.plugin.scanner.DynamicValue;
import com.genai.jmeter.plugin.scanner.WorldView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 3: Intelligent Extraction.
 * Uses specialist AI agents to decide what to correlate and how.
 * Works from WorldView data produced in Phase 2.
 */
public class CorrelationEngine {

    private static final Logger log = LoggerFactory.getLogger(CorrelationEngine.class);

    private static final String SYSTEM_PROMPT = """
            You are a JMeter correlation specialist. Your task is to analyze dynamic values found
            in HTTP traffic and decide exactly how to extract each one using JMeter extractors.

            For each value, determine:
            1. Whether it needs correlation (some values like timestamps don't break replay)
            2. The best extractor type: REGEX, JSON_PATH, XPATH, BOUNDARY, GROOVY, or SKIP
            3. The precise extractor expression
            4. The correct JMeter variable name

            Output ONLY valid JSON — no prose, no markdown, no code blocks.
            Output schema: {"decisions": [{"variable": "str", "type": "REGEX|JSON_PATH|XPATH|BOUNDARY|GROOVY|SKIP",
             "expression": "str", "entryIndex": "str", "confidence": "HIGH|MEDIUM|LOW", "reasoning": "str"}]}
            """;

    private final AIProvider aiProvider;
    private Consumer<String> progressCallback;

    public CorrelationEngine() {
        this.aiProvider = AIProviderFactory.getInstance().getActiveProvider();
    }

    public CorrelationEngine(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
    }

    /**
     * Runs AI-powered correlation on all candidates from the world view.
     * Batches candidates to avoid token limits.
     */
    public List<CorrelationDecision> correlate(WorldView worldView) {
        List<DynamicValue> candidates = worldView.getCorrelationCandidates();
        List<CorrelationDecision> decisions = new ArrayList<>();

        if (candidates.isEmpty()) {
            log.info("No correlation candidates to process");
            return decisions;
        }

        log.info("Correlating {} candidates with AI provider: {}",
                candidates.size(), aiProvider.getName());
        emit("Starting correlation of " + candidates.size() + " dynamic values...");

        // First pass: rule-based decisions for obvious cases (no AI needed)
        List<DynamicValue> aiCandidates = new ArrayList<>();
        for (DynamicValue dv : candidates) {
            CorrelationDecision ruleDecision = applyRules(dv);
            if (ruleDecision != null) {
                decisions.add(ruleDecision);
                emit("Rule-based: " + ruleDecision.getVariableName() + " → " + ruleDecision.getExtractorType());
            } else {
                aiCandidates.add(dv);
            }
        }

        // Second pass: AI for remaining candidates
        if (!aiCandidates.isEmpty() && aiProvider != null && aiProvider.isConfigured()) {
            emit("Sending " + aiCandidates.size() + " candidates to AI (" + aiProvider.getName() + ")...");
            List<CorrelationDecision> aiDecisions = correlateWithAI(aiCandidates, worldView);
            decisions.addAll(aiDecisions);
        } else if (!aiCandidates.isEmpty()) {
            emit("No AI provider configured — applying fallback rules to remaining candidates...");
            for (DynamicValue dv : aiCandidates) {
                decisions.add(fallbackDecision(dv));
            }
        }

        long skipped = decisions.stream().filter(CorrelationDecision::shouldSkip).count();
        long actual = decisions.size() - skipped;
        emit(String.format("Correlation complete: %d extractors, %d skipped", actual, skipped));
        return decisions;
    }

    private List<CorrelationDecision> correlateWithAI(List<DynamicValue> candidates, WorldView worldView) {
        List<CorrelationDecision> decisions = new ArrayList<>();
        int batchSize = 10;

        for (int i = 0; i < candidates.size(); i += batchSize) {
            int end = Math.min(i + batchSize, candidates.size());
            List<DynamicValue> batch = candidates.subList(i, end);
            emit(String.format("Processing batch %d/%d...", (i / batchSize) + 1,
                    (int) Math.ceil(candidates.size() / (double) batchSize)));

            try {
                String prompt = buildBatchPrompt(batch, worldView);
                AIResponse response = aiProvider.chat(SYSTEM_PROMPT, prompt);

                if (response.isSuccess()) {
                    List<CorrelationDecision> batchDecisions = parseAIResponse(response.getText(), batch);
                    decisions.addAll(batchDecisions);
                } else {
                    log.warn("AI response failed for batch: {}", response.getErrorMessage());
                    batch.forEach(dv -> decisions.add(fallbackDecision(dv)));
                }
            } catch (Exception e) {
                log.error("AI correlation error for batch: {}", e.getMessage(), e);
                batch.forEach(dv -> decisions.add(fallbackDecision(dv)));
            }
        }
        return decisions;
    }

    private String buildBatchPrompt(List<DynamicValue> batch, WorldView worldView) {
        StringBuilder sb = new StringBuilder();
        sb.append("Application: ").append(worldView.getBaseDomain()).append("\n");

        if (!worldView.getDetectedFrameworks().isEmpty()) {
            sb.append("Frameworks: ").append(worldView.getDetectedFrameworks().keySet()).append("\n");
        }

        sb.append("\nDynamic values to correlate:\n");
        for (int i = 0; i < batch.size(); i++) {
            DynamicValue dv = batch.get(i);
            sb.append("\n[").append(i).append("] Variable: ").append(dv.getVariableName()).append("\n");
            sb.append("  Value: ").append(dv.getValue().length() > 60
                    ? dv.getValue().substring(0, 60) + "..." : dv.getValue()).append("\n");
            sb.append("  Type: ").append(dv.getValueType()).append("\n");
            sb.append("  Source: ").append(dv.getSourceLocation()).append(" (entry ").append(dv.getSourceEntryIndex()).append(")\n");
            sb.append("  Used in: ").append(dv.getUsages().size()).append(" request(s)\n");
            if (!dv.getUsages().isEmpty()) {
                sb.append("  First usage: ").append(dv.getUsages().get(0).getLocation())
                        .append(" entry ").append(dv.getUsages().get(0).getEntryIndex()).append("\n");
            }
        }

        sb.append("\nReturn JSON decisions array (one per value above, same order, index 0..").append(batch.size() - 1).append(").");
        return sb.toString();
    }

    private List<CorrelationDecision> parseAIResponse(String responseText, List<DynamicValue> batch) {
        List<CorrelationDecision> decisions = new ArrayList<>();
        try {
            String json = extractJson(responseText);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("decisions");

            for (int i = 0; i < arr.size() && i < batch.size(); i++) {
                JsonObject d = arr.get(i).getAsJsonObject();
                DynamicValue dv = batch.get(i);

                String type = getStr(d, "type", "REGEX");
                String expr = getStr(d, "expression", "");
                String varName = getStr(d, "variable", dv.getVariableName());
                String entryIdx = getStr(d, "entryIndex", dv.getSourceEntryIndex());
                String conf = getStr(d, "confidence", "MEDIUM");
                String reason = getStr(d, "reasoning", "");

                CorrelationDecision.ExtractorType extractorType;
                try {
                    extractorType = CorrelationDecision.ExtractorType.valueOf(type.toUpperCase().replace("-", "_"));
                } catch (Exception e) {
                    extractorType = CorrelationDecision.ExtractorType.REGEX;
                }

                CorrelationDecision.Confidence confidence;
                try {
                    confidence = CorrelationDecision.Confidence.valueOf(conf.toUpperCase());
                } catch (Exception e) {
                    confidence = CorrelationDecision.Confidence.MEDIUM;
                }

                if (extractorType == CorrelationDecision.ExtractorType.SKIP || expr.isEmpty()) {
                    decisions.add(new CorrelationDecision(dv, CorrelationDecision.ExtractorType.SKIP,
                            "", varName, entryIdx, reason, confidence));
                } else {
                    decisions.add(new CorrelationDecision(dv, extractorType, expr, varName, entryIdx, reason, confidence));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI response, using fallback: {}", e.getMessage());
            batch.forEach(dv -> decisions.add(fallbackDecision(dv)));
        }

        // Fill any missing decisions
        while (decisions.size() < batch.size()) {
            decisions.add(fallbackDecision(batch.get(decisions.size())));
        }
        return decisions;
    }

    /**
     * Rule-based extraction for clear-cut cases that don't need AI judgment.
     */
    private CorrelationDecision applyRules(DynamicValue dv) {
        String value = dv.getValue();
        String location = dv.getSourceLocation();

        // JWT: always extract from header or JSON response body
        if (dv.getValueType() == DynamicValue.ValueType.JWT) {
            if (location.contains("response.body")) {
                String field = location.contains(".") ? location.substring(location.lastIndexOf('.') + 1) : "token";
                return new CorrelationDecision(dv, CorrelationDecision.ExtractorType.JSON_PATH,
                        "$." + field, dv.getVariableName(), dv.getSourceEntryIndex(),
                        "JWT in JSON response body — JSON path extraction", CorrelationDecision.Confidence.HIGH);
            }
        }

        // Timestamp: usually safe to skip
        if (dv.getValueType() == DynamicValue.ValueType.TIMESTAMP) {
            return new CorrelationDecision(dv, CorrelationDecision.ExtractorType.SKIP,
                    "", dv.getVariableName(), dv.getSourceEntryIndex(),
                    "Timestamp — regenerated automatically; skip correlation", CorrelationDecision.Confidence.HIGH);
        }

        // UUID in JSON response body: JSON path
        if (dv.getValueType() == DynamicValue.ValueType.UUID && location.startsWith("response.body.")) {
            String jsonPath = location.replace("response.body", "$");
            return new CorrelationDecision(dv, CorrelationDecision.ExtractorType.JSON_PATH,
                    jsonPath, dv.getVariableName(), dv.getSourceEntryIndex(),
                    "UUID in JSON body — JSON path extraction", CorrelationDecision.Confidence.HIGH);
        }

        return null; // AI needed
    }

    private CorrelationDecision fallbackDecision(DynamicValue dv) {
        String value = dv.getValue();
        String location = dv.getSourceLocation();

        if (location.startsWith("response.body") && location.contains("$.")) {
            String jsonPath = location.replace("response.body", "$");
            return new CorrelationDecision(dv, CorrelationDecision.ExtractorType.JSON_PATH,
                    jsonPath, dv.getVariableName(), dv.getSourceEntryIndex(),
                    "Fallback: JSON path from source location", CorrelationDecision.Confidence.LOW);
        }

        // Generic regex with boundary markers
        String escaped = java.util.regex.Pattern.quote(value.length() > 20 ? value.substring(0, 20) : value);
        String regex = "\"" + dv.getVariableName().replace("_", "[_\\-]?") + "\"\\s*:\\s*\"([^\"]+)\"";
        return new CorrelationDecision(dv, CorrelationDecision.ExtractorType.REGEX,
                regex, dv.getVariableName(), dv.getSourceEntryIndex(),
                "Fallback: regex around field name", CorrelationDecision.Confidence.LOW);
    }

    private String extractJson(String text) {
        text = text.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String getStr(JsonObject obj, String key, String defaultVal) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : defaultVal;
    }

    private void emit(String message) {
        if (progressCallback != null) progressCallback.accept(message);
        log.info(message);
    }
}
