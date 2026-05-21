package com.genai.jmeter.plugin.maintain;

import com.genai.jmeter.plugin.ai.AIProvider;
import com.genai.jmeter.plugin.ai.AIProviderFactory;
import com.genai.jmeter.plugin.ai.AIResponse;
import com.genai.jmeter.plugin.correlate.CorrelationDecision;
import com.genai.jmeter.plugin.scanner.WorldView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Phase 6: M.I.N.D. — Managed Intelligence for Next-cycle Decisions.
 * Persistent learning system that carries forward validated knowledge.
 * Diagnoses breakages and suggests repairs when applications change.
 */
public class MINDLearningSystem {

    private static final Logger log = LoggerFactory.getLogger(MINDLearningSystem.class);

    private static final String MIND_SYSTEM_PROMPT = """
            You are MIND, a JMeter script self-healing intelligence.
            When given a broken correlation strategy and current traffic observations,
            you diagnose what changed and propose targeted repairs.

            For each broken extractor:
            1. Diagnose: what changed (endpoint, response format, token format, new field name)
            2. Repair: propose updated extractor expression
            3. Confidence: HIGH/MEDIUM/LOW

            Output JSON only:
            {"repairs": [{"variableName":"str","diagnosis":"str",
              "newExpression":"str","newType":"REGEX|JSON_PATH|BOUNDARY|GROOVY",
              "confidence":"HIGH|MEDIUM|LOW"}]}
            """;

    private final GoldenBaseline baseline;
    private final AIProvider aiProvider;
    private final List<LearningEvent> learningHistory = new ArrayList<>();
    private Consumer<String> progressCallback;

    public MINDLearningSystem(GoldenBaseline baseline) {
        this.baseline = baseline;
        this.aiProvider = AIProviderFactory.getInstance().getActiveProvider();
    }

    public void setProgressCallback(Consumer<String> callback) { this.progressCallback = callback; }

    /**
     * Analyses failed decisions from a recent validation run against the baseline
     * and proposes repairs.
     */
    public List<RepairProposal> diagnoseAndRepair(
            List<CorrelationDecision> failedDecisions, WorldView currentWorldView) {

        if (failedDecisions.isEmpty()) {
            emit("MIND: No failures to diagnose");
            return Collections.emptyList();
        }

        emit("MIND: Diagnosing " + failedDecisions.size() + " failed correlations...");

        List<RepairProposal> proposals = new ArrayList<>();

        // Check baseline for previously-working strategies
        for (CorrelationDecision failed : failedDecisions) {
            Optional<GoldenBaseline.BaselineEntry> knownGood =
                    baseline.lookupStrategy(currentWorldView.getBaseDomain(), failed.getVariableName());

            if (knownGood.isPresent()) {
                GoldenBaseline.BaselineEntry entry = knownGood.get();
                emit("MIND: " + failed.getVariableName() + " was previously working (trust=" + entry.trustScore + ")");
                // Reduce trust score
                entry.trustScore = Math.max(0, entry.trustScore - 1);
            }
        }

        // Use AI to diagnose and repair if available
        if (aiProvider != null && aiProvider.isConfigured()) {
            proposals = aiRepair(failedDecisions, currentWorldView);
        } else {
            // Heuristic repair
            for (CorrelationDecision failed : failedDecisions) {
                proposals.add(heuristicRepair(failed, currentWorldView));
            }
        }

        // Record learning event
        learningHistory.add(new LearningEvent(
                new Date(), failedDecisions.size(), proposals.size(),
                currentWorldView.getBaseDomain()));

        emit("MIND: Generated " + proposals.size() + " repair proposals");
        return proposals;
    }

    private List<RepairProposal> aiRepair(List<CorrelationDecision> failed, WorldView worldView) {
        final List<RepairProposal> proposals = new ArrayList<>();
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("Domain: ").append(worldView.getBaseDomain()).append("\n");
            prompt.append("Currently observed dynamic values: ").append(worldView.getDynamicValues().size()).append("\n\n");
            prompt.append("Broken extractors:\n");
            failed.forEach(d -> prompt.append("  - ").append(d.getVariableName())
                    .append(" [").append(d.getExtractorType()).append("]: ")
                    .append(d.getExtractorExpression()).append("\n"));
            prompt.append("\nNew traffic shows these values at similar locations:\n");
            worldView.getDynamicValues().stream().limit(10).forEach(dv ->
                    prompt.append("  ").append(dv.getVariableName()).append(" @ ")
                            .append(dv.getSourceLocation()).append("\n"));

            AIResponse response = aiProvider.chat(MIND_SYSTEM_PROMPT, prompt.toString());
            if (response.isSuccess()) {
                proposals.addAll(parseRepairs(response.getText(), failed));
            }
        } catch (Exception e) {
            log.error("MIND AI repair failed: {}", e.getMessage(), e);
            failed.forEach(d -> proposals.add(heuristicRepair(d, worldView)));
        }
        return proposals;
    }

    private List<RepairProposal> parseRepairs(String text, List<CorrelationDecision> failed) {
        List<RepairProposal> proposals = new ArrayList<>();
        try {
            String json = text.contains("{") ? text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1) : text;
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            root.getAsJsonArray("repairs").forEach(el -> {
                com.google.gson.JsonObject r = el.getAsJsonObject();
                proposals.add(new RepairProposal(
                        getStr(r, "variableName"), getStr(r, "diagnosis"),
                        getStr(r, "newType"), getStr(r, "newExpression"),
                        getStr(r, "confidence")));
            });
        } catch (Exception e) {
            failed.forEach(d -> proposals.add(new RepairProposal(d.getVariableName(),
                    "Parse failed", d.getExtractorType().name(), d.getExtractorExpression(), "LOW")));
        }
        return proposals;
    }

    private RepairProposal heuristicRepair(CorrelationDecision failed, WorldView worldView) {
        // Look for a similar variable name in the new world view
        String varName = failed.getVariableName();
        Optional<com.genai.jmeter.plugin.scanner.DynamicValue> similar = worldView.getDynamicValues().stream()
                .filter(dv -> dv.getVariableName().toLowerCase().contains(varName.toLowerCase().substring(0, Math.min(5, varName.length()))))
                .findFirst();

        if (similar.isPresent()) {
            String newPath = similar.get().getSourceLocation().replace("response.body", "$");
            return new RepairProposal(varName,
                    "Field may have moved — found similar value at " + similar.get().getSourceLocation(),
                    "JSON_PATH", newPath, "MEDIUM");
        }

        return new RepairProposal(varName,
                "Value not found in new traffic — endpoint or response format may have changed",
                failed.getExtractorType().name(), failed.getExtractorExpression(), "LOW");
    }

    private String getStr(com.google.gson.JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private void emit(String msg) {
        if (progressCallback != null) progressCallback.accept(msg);
        log.info(msg);
    }

    public List<LearningEvent> getLearningHistory() { return Collections.unmodifiableList(learningHistory); }

    public static class RepairProposal {
        private final String variableName;
        private final String diagnosis;
        private final String newExtractorType;
        private final String newExpression;
        private final String confidence;

        public RepairProposal(String variableName, String diagnosis, String newExtractorType,
                String newExpression, String confidence) {
            this.variableName = variableName;
            this.diagnosis = diagnosis;
            this.newExtractorType = newExtractorType;
            this.newExpression = newExpression;
            this.confidence = confidence;
        }

        public String getVariableName() { return variableName; }
        public String getDiagnosis() { return diagnosis; }
        public String getNewExtractorType() { return newExtractorType; }
        public String getNewExpression() { return newExpression; }
        public String getConfidence() { return confidence; }

        @Override
        public String toString() {
            return String.format("[%s] %s → %s(%s): %s",
                    confidence, variableName, newExtractorType, newExpression, diagnosis);
        }
    }

    public record LearningEvent(Date timestamp, int failuresAnalyzed, int repairsGenerated, String domain) {}
}
