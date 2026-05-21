package com.genai.jmeter.plugin.scanner;

import com.genai.jmeter.plugin.har.HARModel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 2: Deterministic Observation.
 * Checks every value in every response against every subsequent request.
 * No AI needed — just exhaustive mechanical comparison.
 */
public class DeterministicScanner {

    private static final Logger log = LoggerFactory.getLogger(DeterministicScanner.class);
    private static final int MIN_VALUE_LENGTH = 6;
    private static final int MAX_VALUE_LENGTH = 4096;

    private final TokenDetector tokenDetector = new TokenDetector();

    public WorldView scan(List<HARModel.Entry> entries, String baseDomain) {
        long start = System.currentTimeMillis();
        WorldView worldView = new WorldView(entries, baseDomain);

        log.info("Starting deterministic scan on {} entries", entries.size());

        // Collect all candidate values from responses
        Map<String, DynamicValue> candidateValues = new LinkedHashMap<>();

        for (int i = 0; i < entries.size(); i++) {
            HARModel.Entry entry = entries.get(i);
            if (entry.response == null) continue;

            String entryIndex = String.valueOf(i);
            extractResponseValues(entry, entryIndex, candidateValues, worldView);
        }

        log.info("Extracted {} candidate values from responses", candidateValues.size());

        // Now check each candidate value against all subsequent requests
        for (Map.Entry<String, DynamicValue> e : candidateValues.entrySet()) {
            DynamicValue dv = e.getValue();
            int sourceIdx = Integer.parseInt(dv.getSourceEntryIndex());

            for (int j = sourceIdx + 1; j < entries.size(); j++) {
                HARModel.Entry entry = entries.get(j);
                checkValueInRequest(dv, entry, String.valueOf(j));
            }

            worldView.addDynamicValue(dv);
        }

        detectFrameworks(entries, worldView);
        worldView.setScanDurationMs(System.currentTimeMillis() - start);

        log.info("Scan complete: {} dynamic values, {} correlation candidates, {}ms",
                worldView.getDynamicValues().size(),
                worldView.getCorrelationCandidates().size(),
                worldView.getScanDurationMs());

        return worldView;
    }

    private void extractResponseValues(HARModel.Entry entry, String entryIndex,
            Map<String, DynamicValue> candidates, WorldView worldView) {

        // Extract from response headers
        if (entry.response.headers != null) {
            for (HARModel.NameValuePair header : entry.response.headers) {
                if (isInterestingHeader(header.name)) {
                    processValue(header.value, header.name, "response.header." + header.name,
                            entryIndex, candidates, worldView);
                }
                // Parse Set-Cookie specifically
                if ("set-cookie".equalsIgnoreCase(header.name)) {
                    parseCookieValue(header.value, entryIndex, candidates, worldView);
                }
            }
        }

        // Extract from response body (JSON)
        if (entry.response.content != null && entry.response.content.text != null) {
            String body = entry.response.content.text;
            String ct = entry.response.getContentType();

            if (ct.contains("json")) {
                extractFromJson(body, "response.body", entryIndex, candidates, worldView);
            } else if (ct.contains("html") || ct.contains("text")) {
                extractFromHtml(body, entryIndex, candidates, worldView);
            }
        }
    }

    private void parseCookieValue(String cookieHeader, String entryIndex,
            Map<String, DynamicValue> candidates, WorldView worldView) {
        if (cookieHeader == null) return;
        String[] parts = cookieHeader.split(";");
        if (parts.length > 0) {
            String nameValue = parts[0].trim();
            int eq = nameValue.indexOf('=');
            if (eq > 0) {
                String name = nameValue.substring(0, eq).trim();
                String value = nameValue.substring(eq + 1).trim();
                processValue(value, name, "response.cookie." + name, entryIndex, candidates, worldView);
            }
        }
    }

    private void extractFromJson(String json, String pathPrefix, String entryIndex,
            Map<String, DynamicValue> candidates, WorldView worldView) {
        try {
            JsonElement root = JsonParser.parseString(json);
            extractJsonElement(root, pathPrefix, entryIndex, candidates, worldView);
        } catch (Exception e) {
            // Not valid JSON, skip
        }
    }

    private void extractJsonElement(JsonElement element, String path, String entryIndex,
            Map<String, DynamicValue> candidates, WorldView worldView) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> field : obj.entrySet()) {
                extractJsonElement(field.getValue(), path + "." + field.getKey(),
                        entryIndex, candidates, worldView);
            }
        } else if (element.isJsonArray()) {
            // Only scan first few array elements to avoid explosion
            int limit = Math.min(3, element.getAsJsonArray().size());
            for (int i = 0; i < limit; i++) {
                extractJsonElement(element.getAsJsonArray().get(i),
                        path + "[" + i + "]", entryIndex, candidates, worldView);
            }
        } else if (element.isJsonPrimitive()) {
            String val = element.getAsString();
            String fieldName = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            processValue(val, fieldName, path, entryIndex, candidates, worldView);
        }
    }

    private static final Pattern HIDDEN_INPUT =
            Pattern.compile("<input[^>]+type=['\"]hidden['\"][^>]*name=['\"]([^'\"]+)['\"][^>]*value=['\"]([^'\"]*)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_TOKEN =
            Pattern.compile("<meta[^>]+name=['\"]([^'\"]*(?:token|csrf|xsrf)[^'\"]*)['\"][^>]*content=['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    private void extractFromHtml(String html, String entryIndex,
            Map<String, DynamicValue> candidates, WorldView worldView) {
        Matcher m = HIDDEN_INPUT.matcher(html);
        while (m.find()) {
            processValue(m.group(2), m.group(1), "response.body.input." + m.group(1),
                    entryIndex, candidates, worldView);
        }
        m = META_TOKEN.matcher(html);
        while (m.find()) {
            processValue(m.group(2), m.group(1), "response.body.meta." + m.group(1),
                    entryIndex, candidates, worldView);
        }
    }

    private void processValue(String value, String name, String location, String entryIndex,
            Map<String, DynamicValue> candidates, WorldView worldView) {
        if (value == null || value.trim().isEmpty()) return;
        value = value.trim();
        if (value.length() < MIN_VALUE_LENGTH || value.length() > MAX_VALUE_LENGTH) return;
        if (isStaticValue(value)) return;
        if (candidates.containsKey(value)) return;

        DynamicValue.ValueType type = tokenDetector.inferValueType(name, value);
        String format = tokenDetector.detectFormat(value);
        if (format != null) worldView.addTokenFormat(value, format);

        candidates.put(value, new DynamicValue(value, entryIndex, location, type));
    }

    private void checkValueInRequest(DynamicValue dv, HARModel.Entry entry, String entryIndex) {
        String value = dv.getValue();

        // Check request headers
        if (entry.request.headers != null) {
            for (HARModel.NameValuePair h : entry.request.headers) {
                if (h.value != null && h.value.contains(value)) {
                    dv.addUsage(new DynamicValue.UsagePoint(entryIndex,
                            "request.header." + h.name, h.value));
                }
            }
        }

        // Check URL query params
        if (entry.request.queryString != null) {
            for (HARModel.NameValuePair q : entry.request.queryString) {
                if (q.value != null && q.value.equals(value)) {
                    dv.addUsage(new DynamicValue.UsagePoint(entryIndex,
                            "request.queryParam." + q.name, q.value));
                }
            }
        }

        // Check URL path
        if (entry.request.url != null && entry.request.url.contains(value)) {
            dv.addUsage(new DynamicValue.UsagePoint(entryIndex,
                    "request.url", entry.request.url));
        }

        // Check cookies
        if (entry.request.cookies != null) {
            for (HARModel.NameValuePair c : entry.request.cookies) {
                if (c.value != null && c.value.contains(value)) {
                    dv.addUsage(new DynamicValue.UsagePoint(entryIndex,
                            "request.cookie." + c.name, c.value));
                }
            }
        }

        // Check request body
        if (entry.request.postData != null && entry.request.postData.text != null) {
            if (entry.request.postData.text.contains(value)) {
                dv.addUsage(new DynamicValue.UsagePoint(entryIndex,
                        "request.body", entry.request.postData.text));
            }
        }
        if (entry.request.postData != null && entry.request.postData.params != null) {
            for (HARModel.Param p : entry.request.postData.params) {
                if (p.value != null && p.value.contains(value)) {
                    dv.addUsage(new DynamicValue.UsagePoint(entryIndex,
                            "request.postParam." + p.name, p.value));
                }
            }
        }
    }

    private boolean isInterestingHeader(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.contains("token") || n.contains("auth") || n.contains("session")
                || n.contains("cookie") || n.contains("csrf") || n.contains("xsrf")
                || n.equals("set-cookie") || n.equals("location") || n.contains("x-");
    }

    private boolean isStaticValue(String value) {
        // Skip common static strings
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")
                || value.equalsIgnoreCase("null") || value.equalsIgnoreCase("undefined")) return true;
        if (value.matches("\\d{1,4}")) return true;  // short numbers are likely static IDs
        if (value.startsWith("http://") || value.startsWith("https://")) return false; // URLs can be dynamic
        return false;
    }

    private static final Map<String, String[]> FRAMEWORK_SIGNATURES = new LinkedHashMap<>();

    static {
        FRAMEWORK_SIGNATURES.put("Spring Boot", new String[]{"X-Application-Context", "JSESSIONID", "_csrf"});
        FRAMEWORK_SIGNATURES.put("Django", new String[]{"csrftoken", "csrfmiddlewaretoken", "sessionid"});
        FRAMEWORK_SIGNATURES.put("Rails", new String[]{"authenticity_token", "_rails"});
        FRAMEWORK_SIGNATURES.put("ASP.NET", new String[]{"__RequestVerificationToken", "ASP.NET_SessionId", ".ASPXAUTH"});
        FRAMEWORK_SIGNATURES.put("Laravel", new String[]{"laravel_session", "XSRF-TOKEN"});
        FRAMEWORK_SIGNATURES.put("Express/Node", new String[]{"connect.sid", "x-powered-by: Express"});
    }

    private void detectFrameworks(List<HARModel.Entry> entries, WorldView worldView) {
        Set<String> allValues = new HashSet<>();
        for (HARModel.Entry e : entries) {
            if (e.response != null && e.response.headers != null) {
                for (HARModel.NameValuePair h : e.response.headers) {
                    if (h.name != null) allValues.add(h.name.toLowerCase());
                    if (h.value != null) allValues.add(h.value.toLowerCase());
                }
            }
            if (e.request != null && e.request.cookies != null) {
                for (HARModel.NameValuePair c : e.request.cookies) {
                    if (c.name != null) allValues.add(c.name.toLowerCase());
                }
            }
        }
        for (Map.Entry<String, String[]> fw : FRAMEWORK_SIGNATURES.entrySet()) {
            for (String sig : fw.getValue()) {
                if (allValues.contains(sig.toLowerCase())) {
                    worldView.addFramework(fw.getKey(), "signature: " + sig);
                    break;
                }
            }
        }
    }
}
