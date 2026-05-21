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

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspects captured results for failures (non-2xx, assertion failed) and asks AI
 * to propose JMX fixes — adding pre-processors, headers, extractors, sampler tweaks
 * to make the failing samplers work.
 */
public final class ScriptFixer {

    private static final Logger log = LoggerFactory.getLogger(ScriptFixer.class);

    private static final String SYSTEM_PROMPT =
        "You are a JMeter test plan diagnostic expert.\n" +
        "You will be given a list of FAILED samples (non-2xx response or assertion failures) from a JMeter test run.\n" +
        "For each failure, propose a concrete fix as a JMX modification. Common fix patterns:\n" +
        "  - HTTP 401/403  → add or refresh an Authorization header / extract a token from an earlier sampler\n" +
        "  - HTTP 400      → fix request body, missing parameter, wrong content-type, or correlate a stale value\n" +
        "  - HTTP 404      → fix the path (often a dynamic ID needs correlation)\n" +
        "  - HTTP 5xx      → likely server-side; add a Response Assertion to capture the error pattern\n" +
        "  - Assertion fail → response changed; suggest updated assertion text or extractor\n" +
        "\n" +
        "Reply ONLY with JSON, no markdown:\n" +
        "{\n" +
        "  \"actions\": [\n" +
        "    {\n" +
        "      \"action\": \"add_regex|add_jsonpath|add_boundary|add_jsr223_pre|add_jsr223_post|add_timer|add_assertion|explain\",\n" +
        "      \"targetSampler\": \"exact sampler name where the fix goes (often the sampler BEFORE the failure)\",\n" +
        "      \"variableName\": \"varName or element display name\",\n" +
        "      \"expression\": \"regex/jsonpath\",\n" +
        "      \"leftBoundary\": \"...\",\n" +
        "      \"rightBoundary\": \"...\",\n" +
        "      \"script\": \"groovy script\",\n" +
        "      \"language\": \"groovy\",\n" +
        "      \"assertionContains\": \"text\",\n" +
        "      \"reasoning\": \"why this fixes the failure\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"explanation\": \"summary of the root cause and the fix strategy\"\n" +
        "}\n" +
        "\n" +
        "Be conservative: only propose fixes for failures you're confident about. " +
        "If a failure looks server-side (5xx) and uncorrelated to the script, emit an explain action only.";

    private ScriptFixer() {}

    /**
     * Scan results for failures, ask AI for fixes, return proposals.
     * Blocks on AI call (caller should run in a SwingWorker).
     */
    public static List<ChangeProposal> analyseAndPropose(List<ResultEntry> allResults,
                                                          List<String> samplerNames,
                                                          String additionalLogLines) {
        List<ChangeProposal> proposals = new ArrayList<>();
        if (allResults == null || allResults.isEmpty()) return proposals;

        List<ResultEntry> failures = new ArrayList<>();
        for (ResultEntry e : allResults) {
            int code = e.getStatusCode();
            boolean failed = !e.isSuccess() || code >= 400 || code == 0;
            if (failed) failures.add(e);
        }
        if (failures.isEmpty()) {
            log.info("ScriptFixer: no failures in {} results", allResults.size());
            return proposals;
        }

        AIProvider provider = AIProviderFactory.getInstance().getActiveProvider();
        if (provider == null || !provider.isConfigured()) {
            log.warn("ScriptFixer: no AI provider configured");
            return proposals;
        }

        String prompt = buildPrompt(failures, allResults, samplerNames, additionalLogLines);
        AIResponse resp;
        try {
            resp = provider.chat(SYSTEM_PROMPT, prompt);
        } catch (Exception e) {
            log.error("ScriptFixer: AI call failed", e);
            return proposals;
        }
        if (!resp.isSuccess()) {
            log.warn("ScriptFixer: AI returned error: {}", resp.getErrorMessage());
            return proposals;
        }

        String json = extractJson(resp.getText());
        if (json == null) {
            log.warn("ScriptFixer: AI didn't return JSON");
            return proposals;
        }

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray actions = root.has("actions") && root.get("actions").isJsonArray()
                    ? root.getAsJsonArray("actions") : new JsonArray();
            for (JsonElement el : actions) {
                if (!el.isJsonObject()) continue;
                ChangeProposal p = jsonToProposal(el.getAsJsonObject());
                if (p != null) {
                    p.checkDuplicate();
                    proposals.add(p);
                }
            }
        } catch (Exception e) {
            log.error("ScriptFixer: failed to parse AI JSON", e);
        }
        return proposals;
    }

    private static String buildPrompt(List<ResultEntry> failures, List<ResultEntry> all,
                                       List<String> samplerNames, String logLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("All samplers in current JMX tree (").append(samplerNames.size()).append("):\n");
        for (String s : samplerNames) sb.append("  - ").append(s).append("\n");
        sb.append("\n");

        sb.append("FAILED SAMPLES (").append(failures.size()).append(" of ").append(all.size()).append(" total):\n\n");
        for (ResultEntry f : failures) {
            sb.append("===== ").append("[#").append(f.getIndex()).append("] ").append(f.getSamplerName())
              .append(" — HTTP ").append(f.getStatusCode())
              .append(" (").append(f.getElapsedTime()).append("ms)").append(" =====\n");
            sb.append("URL: ").append(f.getUrl()).append("\n");
            sb.append("REQUEST headers:\n").append(truncate(f.getRequestHeaders(), 800)).append("\n");
            String reqBody = f.getRequestBody();
            if (reqBody != null && !reqBody.isEmpty()) {
                sb.append("REQUEST body:\n").append(truncate(reqBody, 800)).append("\n");
            }
            sb.append("RESPONSE headers:\n").append(truncate(f.getResponseHeaders(), 400)).append("\n");
            sb.append("RESPONSE body:\n").append(truncate(f.getResponseBody(), 1500)).append("\n");

            // Hint: what was the previous successful sampler? (likely source of correlation)
            int idx = all.indexOf(f);
            if (idx > 0) {
                ResultEntry prev = all.get(idx - 1);
                sb.append("PREVIOUS sampler: ").append(prev.getSamplerName())
                  .append(" [HTTP ").append(prev.getStatusCode()).append("]\n");
                String prevBody = prev.getResponseBody();
                if (prevBody != null && !prevBody.isEmpty()) {
                    sb.append("PREVIOUS response (first 800 chars):\n")
                      .append(truncate(prevBody, 800)).append("\n");
                }
            }
            sb.append("\n");
        }

        if (logLines != null && !logLines.isEmpty()) {
            sb.append("\nRecent jmeter.log lines:\n").append(logLines).append("\n");
        }

        sb.append("\nPropose concrete JMX fixes (extractors / pre-processors / assertions / etc.) that would make these samples pass.");
        return sb.toString();
    }

    private static ChangeProposal jsonToProposal(JsonObject obj) {
        String actionStr = getStr(obj, "action", "explain").toLowerCase();
        if ("explain".equals(actionStr)) return null;  // skip pure explanations from fixer

        ChangeProposal p = new ChangeProposal();
        p.action = switch (actionStr) {
            case "add_regex" -> ChangeProposal.Action.ADD_REGEX;
            case "add_jsonpath" -> ChangeProposal.Action.ADD_JSONPATH;
            case "add_boundary" -> ChangeProposal.Action.ADD_BOUNDARY;
            case "add_jsr223_pre", "add_preprocessor" -> ChangeProposal.Action.ADD_JSR223_PRE;
            case "add_jsr223_post", "add_postprocessor" -> ChangeProposal.Action.ADD_JSR223_POST;
            case "add_timer" -> ChangeProposal.Action.ADD_TIMER;
            case "add_assertion" -> ChangeProposal.Action.ADD_ASSERTION;
            default -> ChangeProposal.Action.EXPLAIN;
        };
        if (p.action == ChangeProposal.Action.EXPLAIN) return null;

        p.targetSampler = resolveSampler(getStr(obj, "targetSampler", ""));
        p.variableName = getStr(obj, "variableName", "fix_" + System.nanoTime() % 100000);
        p.expression = getStr(obj, "expression", "");
        p.leftBoundary = getStr(obj, "leftBoundary", "");
        p.rightBoundary = getStr(obj, "rightBoundary", "");
        p.script = getStr(obj, "script", "");
        p.language = getStr(obj, "language", "groovy");
        p.assertionContains = getStr(obj, "assertionContains", "");
        p.reasoning = getStr(obj, "reasoning", "Script Fixer suggestion");
        return p;
    }

    private static String resolveSampler(String name) {
        if (name == null || name.isEmpty()) return null;
        if (LiveJMXModifier.findSampler(name) != null) return name;
        List<String> matches = LiveJMXModifier.findSamplerNamesMatching(name);
        return matches.isEmpty() ? name : matches.get(0);
    }

    private static String getStr(JsonObject obj, String key, String def) {
        try { return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def; }
        catch (Exception e) { return def; }
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
