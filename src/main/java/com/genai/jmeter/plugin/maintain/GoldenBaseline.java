package com.genai.jmeter.plugin.maintain;

import com.genai.jmeter.plugin.correlate.CorrelationDecision;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Phase 6: Self-Healing.
 * Maintains the golden baseline — validated extraction strategies that represent
 * the known-good state of the script. Persisted as JSON to disk.
 */
public class GoldenBaseline {

    private static final Logger log = LoggerFactory.getLogger(GoldenBaseline.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path baselineDir;
    private final Map<String, BaselineEntry> entries = new LinkedHashMap<>();
    private Instant lastUpdated;

    public GoldenBaseline(Path baselineDir) {
        this.baselineDir = baselineDir;
        try {
            Files.createDirectories(baselineDir);
        } catch (IOException e) {
            log.warn("Could not create baseline directory: {}", e.getMessage());
        }
    }

    public static GoldenBaseline defaultBaseline() {
        Path dir = Paths.get(System.getProperty("user.home"), ".genai-jmeter", "baselines");
        return new GoldenBaseline(dir);
    }

    /**
     * Promotes validated decisions to the golden baseline.
     */
    public void promoteToBaseline(String scriptId, List<CorrelationDecision> decisions, String domain) {
        for (CorrelationDecision decision : decisions) {
            if (!decision.shouldSkip() && decision.isValidated()) {
                String key = domain + ":" + decision.getVariableName();
                BaselineEntry existing = entries.get(key);
                int trustScore = existing != null ? Math.min(existing.trustScore + 1, 100) : 1;

                entries.put(key, new BaselineEntry(
                        key, scriptId, domain,
                        decision.getVariableName(),
                        decision.getExtractorType().name(),
                        decision.getExtractorExpression(),
                        decision.getExtractionEntryIndex(),
                        trustScore, Instant.now().toString()));
            }
        }
        lastUpdated = Instant.now();
        save(scriptId);
        log.info("Promoted {} validated decisions to golden baseline for {}", decisions.size(), domain);
    }

    public Optional<BaselineEntry> lookupStrategy(String domain, String variableName) {
        String key = domain + ":" + variableName;
        return Optional.ofNullable(entries.get(key));
    }

    /**
     * Returns trusted entries (trust score >= 3) for a given domain.
     */
    public List<BaselineEntry> getTrustedStrategies(String domain) {
        List<BaselineEntry> trusted = new ArrayList<>();
        entries.values().stream()
                .filter(e -> e.domain.equals(domain) && e.trustScore >= 3)
                .forEach(trusted::add);
        return trusted;
    }

    public void save(String scriptId) {
        Path file = baselineDir.resolve(sanitize(scriptId) + ".json");
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("scriptId", scriptId);
            data.put("lastUpdated", lastUpdated != null ? lastUpdated.toString() : Instant.now().toString());
            data.put("entries", new ArrayList<>(entries.values()));
            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
            log.info("Baseline saved to {}", file);
        } catch (IOException e) {
            log.error("Failed to save baseline: {}", e.getMessage());
        }
    }

    public boolean load(String scriptId) {
        Path file = baselineDir.resolve(sanitize(scriptId) + ".json");
        if (!Files.exists(file)) return false;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = GSON.fromJson(json, Map.class);
            entries.clear();
            // Reload entries
            log.info("Baseline loaded from {}", file);
            return true;
        } catch (IOException e) {
            log.error("Failed to load baseline: {}", e.getMessage());
            return false;
        }
    }

    private String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    public Map<String, BaselineEntry> getEntries() { return Collections.unmodifiableMap(entries); }
    public Instant getLastUpdated() { return lastUpdated; }
    public int size() { return entries.size(); }

    public static class BaselineEntry {
        public final String key;
        public final String scriptId;
        public final String domain;
        public final String variableName;
        public final String extractorType;
        public final String expression;
        public final String sourceEntryIndex;
        public int trustScore;
        public final String validatedAt;

        public BaselineEntry(String key, String scriptId, String domain, String variableName,
                String extractorType, String expression, String sourceEntryIndex,
                int trustScore, String validatedAt) {
            this.key = key;
            this.scriptId = scriptId;
            this.domain = domain;
            this.variableName = variableName;
            this.extractorType = extractorType;
            this.expression = expression;
            this.sourceEntryIndex = sourceEntryIndex;
            this.trustScore = trustScore;
            this.validatedAt = validatedAt;
        }
    }
}
