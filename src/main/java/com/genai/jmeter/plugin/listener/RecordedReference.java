package com.genai.jmeter.plugin.listener;

import com.genai.jmeter.plugin.har.HARModel;
import com.genai.jmeter.plugin.har.HARParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loaded once by the user to give AI a baseline of what was "recorded".
 * Two source types are supported:
 *   1. JMX file (recorder output) — extracts sampler name → original path / args
 *   2. HAR file — extracts URL → original request + response per entry
 *
 * The captured baseline is sent to the AI alongside the live response so it can compare
 * the original (recorded) values with the current run and propose accurate correlations.
 */
public class RecordedReference {

    private static final Logger log = LoggerFactory.getLogger(RecordedReference.class);

    public enum SourceType { JMX, HAR, NONE }

    private SourceType type = SourceType.NONE;
    private String sourcePath = "";
    private final Map<String, RecordedSample> samples = new LinkedHashMap<>();

    public boolean isLoaded() { return type != SourceType.NONE && !samples.isEmpty(); }
    public SourceType getType() { return type; }
    public String getSourcePath() { return sourcePath; }
    public Map<String, RecordedSample> getSamples() { return samples; }
    public int size() { return samples.size(); }

    public void clear() {
        samples.clear();
        sourcePath = "";
        type = SourceType.NONE;
    }

    /**
     * Loads a JMX or HAR file. Throws if the file isn't parseable.
     */
    public void loadFrom(File file) throws Exception {
        if (file == null || !file.exists()) throw new IllegalArgumentException("File not found: " + file);
        String name = file.getName().toLowerCase();
        clear();
        if (name.endsWith(".har")) {
            loadHar(file);
            type = SourceType.HAR;
        } else if (name.endsWith(".jmx") || name.endsWith(".xml")) {
            loadJmx(file);
            type = SourceType.JMX;
        } else {
            throw new IllegalArgumentException("Unsupported file type. Use .jmx or .har");
        }
        sourcePath = file.getAbsolutePath();
        log.info("Loaded recorded reference ({}) — {} samples from {}", type, samples.size(), sourcePath);
    }

    private void loadHar(File file) throws Exception {
        HARParser parser = new HARParser();
        HARModel har = parser.parse(file);
        int idx = 0;
        for (HARModel.Entry e : har.log.entries) {
            String key = "entry_" + (idx++);
            if (e.request != null && e.request.url != null) {
                key = e.request.method + " " + truncate(e.request.url, 100);
            }
            RecordedSample s = new RecordedSample();
            s.name = key;
            if (e.request != null) {
                s.method = e.request.method;
                s.url = e.request.url;
                if (e.request.postData != null) s.requestBody = e.request.postData.text;
                if (e.request.queryString != null) {
                    StringBuilder sb = new StringBuilder();
                    e.request.queryString.forEach(nv -> sb.append(nv.name).append("=").append(nv.value).append("&"));
                    s.queryString = sb.toString();
                }
            }
            if (e.response != null) {
                s.responseStatus = e.response.status;
                if (e.response.content != null) {
                    s.responseBody = e.response.content.text;
                }
            }
            samples.put(key, s);
        }
    }

    private void loadJmx(File file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(file);
        // Find all HTTPSamplerProxy elements
        NodeList samplers = doc.getElementsByTagName("HTTPSamplerProxy");
        for (int i = 0; i < samplers.getLength(); i++) {
            Element s = (Element) samplers.item(i);
            String name = s.getAttribute("testname");
            if (name == null || name.isEmpty()) name = "Sampler_" + i;

            RecordedSample rs = new RecordedSample();
            rs.name = name;
            rs.method = stringProp(s, "HTTPSampler.method", "GET");
            String domain = stringProp(s, "HTTPSampler.domain", "");
            String port = stringProp(s, "HTTPSampler.port", "");
            String protocol = stringProp(s, "HTTPSampler.protocol", "https");
            String path = stringProp(s, "HTTPSampler.path", "");
            rs.url = protocol + "://" + domain + (port.isEmpty() ? "" : ":" + port) + path;
            rs.requestBody = extractArguments(s);

            samples.put(name, rs);
        }
    }

    private String stringProp(Element parent, String propName, String defaultValue) {
        NodeList props = parent.getElementsByTagName("stringProp");
        for (int i = 0; i < props.getLength(); i++) {
            Element p = (Element) props.item(i);
            if (propName.equals(p.getAttribute("name"))) {
                return p.getTextContent();
            }
        }
        return defaultValue;
    }

    private String extractArguments(Element sampler) {
        StringBuilder sb = new StringBuilder();
        NodeList args = sampler.getElementsByTagName("elementProp");
        for (int i = 0; i < args.getLength(); i++) {
            Element ep = (Element) args.item(i);
            if (!"HTTPArgument".equals(ep.getAttribute("elementType"))) continue;
            String name = ep.getAttribute("name");
            // Look for Argument.value stringProp child
            NodeList children = ep.getElementsByTagName("stringProp");
            String value = "";
            for (int j = 0; j < children.getLength(); j++) {
                Element c = (Element) children.item(j);
                if ("Argument.value".equals(c.getAttribute("name"))) {
                    value = c.getTextContent();
                    break;
                }
            }
            if (!name.isEmpty() || !value.isEmpty()) {
                sb.append(name).append("=").append(value).append("&");
            }
        }
        return sb.toString();
    }

    /**
     * Returns a compact textual summary suitable for sending to the AI as additional context.
     * Caps each sample at ~500 chars to keep prompt size sane.
     */
    public String summariseForAI(int maxSamples) {
        if (!isLoaded()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Recorded baseline (").append(type).append(", ").append(samples.size()).append(" samples):\n");
        int n = 0;
        for (Map.Entry<String, RecordedSample> e : samples.entrySet()) {
            if (n++ >= maxSamples) {
                sb.append("  ... (").append(samples.size() - maxSamples).append(" more, omitted)\n");
                break;
            }
            RecordedSample s = e.getValue();
            sb.append("- ").append(s.name);
            if (s.method != null) sb.append(" [").append(s.method).append("]");
            if (s.url != null) sb.append(" ").append(truncate(s.url, 80));
            sb.append("\n");
            if (s.requestBody != null && !s.requestBody.isEmpty()) {
                sb.append("    request: ").append(truncate(s.requestBody, 200)).append("\n");
            }
            if (s.responseBody != null && !s.responseBody.isEmpty()) {
                sb.append("    response (first 300): ").append(truncate(s.responseBody, 300)).append("\n");
            }
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", "");
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** Per-sample recorded data. */
    public static class RecordedSample {
        public String name;
        public String method;
        public String url;
        public String queryString;
        public String requestBody;
        public int responseStatus;
        public String responseBody;
    }
}
