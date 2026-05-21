package com.genai.jmeter.plugin.har;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HARParser {

    private static final Logger log = LoggerFactory.getLogger(HARParser.class);
    private static final Gson gson = new GsonBuilder().create();

    public HARModel parse(File harFile) throws IOException {
        String content = FileUtils.readFileToString(harFile, StandardCharsets.UTF_8);
        return parse(content);
    }

    public HARModel parse(String jsonContent) {
        HARModel model = gson.fromJson(jsonContent, HARModel.class);
        validate(model);
        return model;
    }

    private void validate(HARModel model) {
        if (model == null || model.log == null) {
            throw new IllegalArgumentException("Invalid HAR file: missing root 'log' object");
        }
        if (model.log.entries == null || model.log.entries.isEmpty()) {
            throw new IllegalArgumentException("HAR file contains no entries");
        }
        log.info("HAR parsed: {} entries from {}", model.log.entries.size(),
                model.log.creator != null ? model.log.creator.name : "unknown");
    }

    /**
     * Filters out non-essential entries: images, fonts, CSS, analytics beacons.
     */
    public List<HARModel.Entry> filterRelevantEntries(HARModel model) {
        List<HARModel.Entry> relevant = new ArrayList<>();
        for (HARModel.Entry entry : model.log.entries) {
            if (isRelevant(entry)) {
                relevant.add(entry);
            }
        }
        log.info("Filtered to {} relevant entries from {}", relevant.size(), model.log.entries.size());
        return relevant;
    }

    private boolean isRelevant(HARModel.Entry entry) {
        if (entry.request == null || entry.request.url == null) return false;
        String url = entry.request.url.toLowerCase();

        // Skip static assets
        if (url.matches(".*\\.(png|jpg|jpeg|gif|svg|ico|woff|woff2|ttf|eot|css|map)([?#].*)?$")) return false;

        // Skip common analytics and tracking
        if (url.contains("google-analytics") || url.contains("googletagmanager")
                || url.contains("analytics") || url.contains("tracking")
                || url.contains("hotjar") || url.contains("mixpanel")) return false;

        // Skip data URIs
        if (url.startsWith("data:")) return false;

        String contentType = entry.response != null ? entry.response.getContentType() : "";

        // Keep JSON, HTML, form data, XML
        if (contentType.contains("json") || contentType.contains("html")
                || contentType.contains("form") || contentType.contains("xml")
                || contentType.contains("text/plain")) return true;

        // Keep if it's a POST/PUT/DELETE regardless of content type
        if ("POST".equalsIgnoreCase(entry.request.method)
                || "PUT".equalsIgnoreCase(entry.request.method)
                || "DELETE".equalsIgnoreCase(entry.request.method)
                || "PATCH".equalsIgnoreCase(entry.request.method)) return true;

        // Keep GET requests that don't have static extensions
        return "GET".equalsIgnoreCase(entry.request.method);
    }

    /**
     * Extracts the base domain from a HAR file to identify the application under test.
     */
    public String extractBaseDomain(HARModel model) {
        if (model.log.entries == null || model.log.entries.isEmpty()) return "";
        try {
            URI uri = new URI(model.log.entries.get(0).request.url);
            return uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (URISyntaxException e) {
            return "";
        }
    }
}
