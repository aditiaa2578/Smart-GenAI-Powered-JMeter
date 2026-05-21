package com.genai.jmeter.plugin.assess;

import com.genai.jmeter.plugin.ai.AIProvider;
import com.genai.jmeter.plugin.ai.AIProviderFactory;
import com.genai.jmeter.plugin.ai.AIResponse;
import com.genai.jmeter.plugin.correlate.CorrelationDecision;
import com.genai.jmeter.plugin.har.HARModel;
import com.genai.jmeter.plugin.scanner.DynamicValue;
import com.genai.jmeter.plugin.scanner.WorldView;
import com.genai.jmeter.plugin.validate.ValidationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 5: Quality Gate.
 * SCOUT analyses the correlated script against the original recording
 * and produces a structured quality report.
 */
public class SCOUTAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SCOUTAnalyzer.class);

    private static final String SCOUT_SYSTEM_PROMPT = """
            You are SCOUT, a JMeter script quality analyst.
            Analyse the correlation strategy against the HAR recording and produce:
            - Risk flags for uncorrelated dynamic values (severity: HIGH/MEDIUM/LOW)
            - Specific actionable recommendations
            - An overall production-readiness assessment

            Output JSON only, no prose:
            {"riskFlags": [{"severity":"HIGH","title":"str","description":"str","entryIndex":"str"}],
             "recommendations": [{"title":"str","action":"str","entryIndex":"str"}],
             "additionalInsights": "str"}
            """;

    private final AIProvider aiProvider;
    private Consumer<String> progressCallback;

    public SCOUTAnalyzer() {
        this.aiProvider = AIProviderFactory.getInstance().getActiveProvider();
    }

    public SCOUTAnalyzer(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public void setProgressCallback(Consumer<String> callback) { this.progressCallback = callback; }

    public QualityReport assess(WorldView worldView,
            List<CorrelationDecision> decisions,
            ValidationEngine.ValidationResult validationResult) {

        emit("SCOUT: Starting quality assessment...");

        QualityReport.Builder builder = new QualityReport.Builder();
        List<HARModel.Entry> entries = worldView.getEntries();
        List<DynamicValue> allDynamic = worldView.getDynamicValues();
        List<DynamicValue> candidates = worldView.getCorrelationCandidates();

        int totalRequests = entries.size();
        int correlatedCount = (int) decisions.stream().filter(d -> !d.shouldSkip()).count();
        int uncorrelated = (int) allDynamic.stream()
                .filter(dv -> dv.isUsed() && decisions.stream()
                        .noneMatch(d -> !d.shouldSkip()
                                && d.getDynamicValue().getVariableName().equals(dv.getVariableName())))
                .count();

        // Calculate per-request coverage
        for (int i = 0; i < entries.size(); i++) {
            HARModel.Entry entry = entries.get(i);
            double coverage = calculateRequestCoverage(entry, String.valueOf(i), decisions);
            builder.addRequestCoverage(new QualityReport.RequestCoverage(
                    String.valueOf(i), truncateUrl(entry.getUrl()), coverage,
                    (int) ((1.0 - coverage) * 5)));
        }

        // Rule-based risk flags
        addRuleBasedRisks(allDynamic, decisions, builder);

        // AI-powered analysis if available
        if (aiProvider != null && aiProvider.isConfigured()) {
            emit("SCOUT: Requesting AI quality analysis...");
            addAIInsights(worldView, decisions, validationResult, builder);
        }

        // Compute overall score
        double correlationScore = candidates.isEmpty() ? 1.0
                : (double) correlatedCount / Math.max(1, candidates.size());
        double validationScore = validationResult != null ? validationResult.getSuccessRate() : 1.0;
        double overallScore = (correlationScore * 0.6) + (validationScore * 0.4);
        overallScore = Math.min(1.0, Math.max(0.0, overallScore));

        builder.overallScore(overallScore)
                .totalRequests(totalRequests)
                .correlatedRequests(correlatedCount)
                .uncorrelatedDynamicValues(uncorrelated);

        QualityReport report = builder.build();
        emit("SCOUT: Assessment complete — Grade " + report.getOverallGrade()
                + " (" + String.format("%.0f%%", overallScore * 100) + ")");
        return report;
    }

    private double calculateRequestCoverage(HARModel.Entry entry, String entryIdx,
            List<CorrelationDecision> decisions) {
        long relatedDecisions = decisions.stream()
                .filter(d -> entryIdx.equals(d.getExtractionEntryIndex()) && !d.shouldSkip())
                .count();
        long relatedUsages = decisions.stream()
                .flatMap(d -> d.getDynamicValue().getUsages().stream())
                .filter(u -> entryIdx.equals(u.getEntryIndex()))
                .count();
        if (relatedUsages == 0) return 1.0;
        return Math.min(1.0, (double) relatedDecisions / relatedUsages);
    }

    private void addRuleBasedRisks(List<DynamicValue> allDynamic,
            List<CorrelationDecision> decisions, QualityReport.Builder builder) {

        for (DynamicValue dv : allDynamic) {
            if (!dv.isUsed()) continue;
            boolean hasDecision = decisions.stream().anyMatch(d ->
                    !d.shouldSkip() && d.getDynamicValue().getVariableName().equals(dv.getVariableName()));

            if (!hasDecision) {
                String severity = dv.getValueType() == DynamicValue.ValueType.AUTH_HEADER
                        || dv.getValueType() == DynamicValue.ValueType.CSRF_TOKEN
                        || dv.getValueType() == DynamicValue.ValueType.SESSION_TOKEN ? "HIGH" : "MEDIUM";

                builder.addRiskFlag(new QualityReport.RiskFlag(severity,
                        "Uncorrelated: " + dv.getVariableName(),
                        "Dynamic value used in " + dv.getUsages().size() + " request(s) but not correlated",
                        dv.getSourceEntryIndex()));

                builder.addRecommendation(new QualityReport.Recommendation(
                        "Add extractor for " + dv.getVariableName(),
                        "Extract from entry " + dv.getSourceEntryIndex() + " at " + dv.getSourceLocation(),
                        dv.getSourceEntryIndex()));
            }
        }

        // Flag low-confidence decisions
        decisions.stream()
                .filter(d -> d.getConfidence() == CorrelationDecision.Confidence.LOW && !d.shouldSkip())
                .forEach(d -> builder.addRiskFlag(new QualityReport.RiskFlag("LOW",
                        "Low-confidence extractor: " + d.getVariableName(),
                        "Review the expression: " + d.getExtractorExpression(),
                        d.getExtractionEntryIndex())));
    }

    private void addAIInsights(WorldView worldView, List<CorrelationDecision> decisions,
            ValidationEngine.ValidationResult validationResult, QualityReport.Builder builder) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("HAR domain: ").append(worldView.getBaseDomain()).append("\n");
            prompt.append("Frameworks: ").append(worldView.getDetectedFrameworks().keySet()).append("\n");
            prompt.append("Total dynamic values: ").append(worldView.getDynamicValues().size()).append("\n");
            prompt.append("Correlation candidates: ").append(worldView.getCorrelationCandidates().size()).append("\n");
            prompt.append("Correlated: ").append(decisions.stream().filter(d -> !d.shouldSkip()).count()).append("\n");
            if (validationResult != null) {
                prompt.append("Validation: ").append(validationResult.getSummary()).append("\n");
            }
            prompt.append("\nDecisions summary:\n");
            decisions.stream().filter(d -> !d.shouldSkip()).limit(10).forEach(d ->
                    prompt.append("  ").append(d.getVariableName())
                            .append(" → ").append(d.getExtractorType())
                            .append(" [").append(d.getConfidence()).append("]\n"));

            AIResponse response = aiProvider.chat(SCOUT_SYSTEM_PROMPT, prompt.toString());
            if (response.isSuccess()) {
                parseAIInsights(response.getText(), builder);
            }
        } catch (Exception e) {
            log.warn("SCOUT AI analysis failed: {}", e.getMessage());
        }
    }

    private void parseAIInsights(String text, QualityReport.Builder builder) {
        try {
            String json = text.contains("{") ? text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1) : text;
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

            if (root.has("riskFlags")) {
                root.getAsJsonArray("riskFlags").forEach(el -> {
                    com.google.gson.JsonObject f = el.getAsJsonObject();
                    builder.addRiskFlag(new QualityReport.RiskFlag(
                            getStr(f, "severity", "LOW"), getStr(f, "title", ""),
                            getStr(f, "description", ""), getStr(f, "entryIndex", "")));
                });
            }
            if (root.has("recommendations")) {
                root.getAsJsonArray("recommendations").forEach(el -> {
                    com.google.gson.JsonObject r = el.getAsJsonObject();
                    builder.addRecommendation(new QualityReport.Recommendation(
                            getStr(r, "title", ""), getStr(r, "action", ""),
                            getStr(r, "entryIndex", "")));
                });
            }
        } catch (Exception e) {
            log.warn("Failed to parse SCOUT AI insights: {}", e.getMessage());
        }
    }

    private String getStr(com.google.gson.JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private String truncateUrl(String url) {
        if (url == null) return "";
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getPath();
        } catch (Exception e) {
            return url.length() > 60 ? url.substring(0, 60) + "..." : url;
        }
    }

    private void emit(String msg) {
        if (progressCallback != null) progressCallback.accept(msg);
        log.info(msg);
    }
}
