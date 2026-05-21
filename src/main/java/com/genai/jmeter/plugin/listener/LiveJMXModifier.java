package com.genai.jmeter.plugin.listener;

import org.apache.jmeter.assertions.ResponseAssertion;
import org.apache.jmeter.assertions.gui.AssertionGui;
import org.apache.jmeter.extractor.BoundaryExtractor;
import org.apache.jmeter.extractor.JSR223PostProcessor;
import org.apache.jmeter.extractor.RegexExtractor;
import org.apache.jmeter.extractor.gui.BoundaryExtractorGui;
import org.apache.jmeter.extractor.gui.RegexExtractorGui;
import org.apache.jmeter.extractor.json.jsonpath.JSONPostProcessor;
import org.apache.jmeter.extractor.json.jsonpath.gui.JSONPostProcessorGui;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.modifiers.JSR223PreProcessor;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testbeans.gui.TestBeanGUI;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.timers.ConstantTimer;
import org.apache.jmeter.timers.gui.ConstantTimerGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Helper that modifies the currently-loaded JMeter test tree at runtime.
 * Used by the SmartCorrelationListener to add extractors / processors / assertions
 * under the matching sampler — no separate JMX needed.
 */
public final class LiveJMXModifier {

    private static final Logger log = LoggerFactory.getLogger(LiveJMXModifier.class);

    private LiveJMXModifier() {}

    // ── Regex Post-Processor ──────────────────────────────────────────────────
    public static boolean addRegexExtractor(String samplerName, String varName, String regex) {
        return addRegexExtractor(samplerName, varName, regex, "NOT_FOUND", 1, "$1$");
    }

    public static boolean addRegexExtractor(String samplerName, String varName, String regex,
                                             String defaultValue, int matchNumber, String template) {
        return onEDT(() -> {
            JMeterTreeNode sampler = findSampler(samplerName);
            if (sampler == null) {
                log.warn("Could not find sampler named '{}' in current tree", samplerName);
                return false;
            }

            RegexExtractor extractor = new RegexExtractor();
            extractor.setProperty(TestElement.GUI_CLASS, RegexExtractorGui.class.getName());
            extractor.setProperty(TestElement.TEST_CLASS, RegexExtractor.class.getName());
            extractor.setName("Regex Extract: " + varName);
            extractor.setRefName(varName);
            extractor.setRegex(regex);
            extractor.setTemplate(template);
            extractor.setMatchNumber(matchNumber);
            extractor.setDefaultValue(defaultValue);
            extractor.setUseField("false");
            return addUnder(sampler, extractor);
        });
    }

    // ── JSONPath Post-Processor ───────────────────────────────────────────────
    public static boolean addJsonPathExtractor(String samplerName, String varName, String jsonPath) {
        return onEDT(() -> {
            JMeterTreeNode sampler = findSampler(samplerName);
            if (sampler == null) return false;

            JSONPostProcessor extractor = new JSONPostProcessor();
            extractor.setProperty(TestElement.GUI_CLASS, JSONPostProcessorGui.class.getName());
            extractor.setProperty(TestElement.TEST_CLASS, JSONPostProcessor.class.getName());
            extractor.setName("JSONPath Extract: " + varName);
            extractor.setRefNames(varName);
            extractor.setJsonPathExpressions(jsonPath);
            extractor.setMatchNumbers("1");
            extractor.setDefaultValues("NOT_FOUND");
            return addUnder(sampler, extractor);
        });
    }

    // ── Boundary Extractor ────────────────────────────────────────────────────
    public static boolean addBoundaryExtractor(String samplerName, String varName,
                                                String leftBound, String rightBound) {
        return onEDT(() -> {
            JMeterTreeNode sampler = findSampler(samplerName);
            if (sampler == null) return false;

            BoundaryExtractor extractor = new BoundaryExtractor();
            extractor.setProperty(TestElement.GUI_CLASS, BoundaryExtractorGui.class.getName());
            extractor.setProperty(TestElement.TEST_CLASS, BoundaryExtractor.class.getName());
            extractor.setName("Boundary Extract: " + varName);
            extractor.setRefName(varName);
            extractor.setLeftBoundary(leftBound);
            extractor.setRightBoundary(rightBound);
            extractor.setMatchNumber(1);
            extractor.setDefaultValue("NOT_FOUND");
            return addUnder(sampler, extractor);
        });
    }

    // ── JSR223 PreProcessor ───────────────────────────────────────────────────
    public static boolean addJsr223PreProcessor(String samplerName, String name, String script, String language) {
        return addJsr223(samplerName, name, script, language, true);
    }

    // ── JSR223 PostProcessor ──────────────────────────────────────────────────
    public static boolean addJsr223PostProcessor(String samplerName, String name, String script, String language) {
        return addJsr223(samplerName, name, script, language, false);
    }

    private static boolean addJsr223(String samplerName, String name, String script, String language, boolean isPre) {
        return onEDT(() -> {
            JMeterTreeNode sampler = findSampler(samplerName);
            if (sampler == null) return false;

            TestElement elem;
            String displayName;
            if (isPre) {
                JSR223PreProcessor pre = new JSR223PreProcessor();
                pre.setProperty(TestElement.GUI_CLASS, TestBeanGUI.class.getName());
                pre.setProperty(TestElement.TEST_CLASS, JSR223PreProcessor.class.getName());
                elem = pre;
                displayName = "JSR223 PreProcessor";
            } else {
                JSR223PostProcessor post = new JSR223PostProcessor();
                post.setProperty(TestElement.GUI_CLASS, TestBeanGUI.class.getName());
                post.setProperty(TestElement.TEST_CLASS, JSR223PostProcessor.class.getName());
                elem = post;
                displayName = "JSR223 PostProcessor";
            }
            elem.setName(name != null && !name.isEmpty() ? name : displayName);

            // JSR223 elements use TestBean introspection — these are the property names the BeanInfo expects
            String lang = normaliseLanguage(language);
            elem.setProperty("scriptLanguage", lang);
            elem.setProperty("parameters", "");
            elem.setProperty("filename", "");
            elem.setProperty("cacheKey", "true");
            elem.setProperty("script", script != null ? script : "");

            return addUnder(sampler, elem);
        });
    }

    /** Map common language aliases to JMeter's expected scriptLanguage values. */
    private static String normaliseLanguage(String lang) {
        if (lang == null || lang.isBlank()) return "groovy";
        String l = lang.toLowerCase().trim();
        return switch (l) {
            case "groovy" -> "groovy";
            case "beanshell", "bsh" -> "beanshell";
            case "javascript", "js", "nashorn" -> "nashorn";
            case "python", "jython" -> "jython";
            default -> "groovy";
        };
    }

    // ── Constant Timer ────────────────────────────────────────────────────────
    public static boolean addConstantTimer(String samplerName, long delayMs) {
        return onEDT(() -> {
            JMeterTreeNode sampler = findSampler(samplerName);
            if (sampler == null) return false;

            ConstantTimer timer = new ConstantTimer();
            timer.setProperty(TestElement.GUI_CLASS, ConstantTimerGui.class.getName());
            timer.setProperty(TestElement.TEST_CLASS, ConstantTimer.class.getName());
            timer.setName("Constant Timer (" + delayMs + " ms)");
            timer.setDelay(String.valueOf(delayMs));
            return addUnder(sampler, timer);
        });
    }

    // ── Response Assertion ────────────────────────────────────────────────────
    public static boolean addResponseAssertion(String samplerName, String pattern, int testType) {
        return onEDT(() -> {
            JMeterTreeNode sampler = findSampler(samplerName);
            if (sampler == null) return false;

            ResponseAssertion assertion = new ResponseAssertion();
            assertion.setProperty(TestElement.GUI_CLASS, AssertionGui.class.getName());
            assertion.setProperty(TestElement.TEST_CLASS, ResponseAssertion.class.getName());
            assertion.setName("Response Assertion: contains '" + truncate(pattern, 24) + "'");
            assertion.addTestString(pattern);
            assertion.setTestFieldResponseData();
            assertion.setToContainsType();
            return addUnder(sampler, assertion);
        });
    }

    // ── Substitute value across samplers (request bodies, paths, params) ──────
    public static int substituteValueWithVariable(String oldValue, String varName) {
        if (oldValue == null || oldValue.isEmpty() || varName == null || varName.isEmpty()) return 0;
        final int[] count = {0};
        onEDT(() -> {
            GuiPackage gui = GuiPackage.getInstance();
            if (gui == null) return false;
            JMeterTreeModel model = gui.getTreeModel();
            JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
            walkSubstitute(root, oldValue, "${" + varName + "}", count);
            return true;
        });
        return count[0];
    }

    @SuppressWarnings("unchecked")
    private static void walkSubstitute(JMeterTreeNode node, String oldVal, String newVal, int[] count) {
        TestElement te = node.getTestElement();
        if (te instanceof HTTPSamplerBase http) {
            String path = http.getPath();
            if (path != null && path.contains(oldVal)) {
                http.setPath(path.replace(oldVal, newVal));
                count[0]++;
            }
            try {
                org.apache.jmeter.config.Arguments args = http.getArguments();
                if (args != null) {
                    for (int i = 0; i < args.getArgumentCount(); i++) {
                        org.apache.jmeter.config.Argument a = args.getArgument(i);
                        String v = a.getValue();
                        if (v != null && v.contains(oldVal)) {
                            a.setValue(v.replace(oldVal, newVal));
                            count[0]++;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            walkSubstitute((JMeterTreeNode) children.nextElement(), oldVal, newVal, count);
        }
    }

    // ── Core helpers ──────────────────────────────────────────────────────────

    private static boolean addUnder(JMeterTreeNode parent, TestElement element) {
        try {
            GuiPackage gui = GuiPackage.getInstance();
            if (gui == null) return false;
            JMeterTreeModel model = gui.getTreeModel();
            JMeterTreeNode newNode = model.addComponent(element, parent);
            gui.getMainFrame().repaint();
            log.info("Added '{}' under '{}'", element.getName(), parent.getName());
            return newNode != null;
        } catch (Exception e) {
            log.error("Failed to add element {} under {}: {}", element.getName(), parent.getName(), e.getMessage(), e);
            return false;
        }
    }

    /** Find a sampler node by exact name (case-insensitive). Returns the first match. */
    public static JMeterTreeNode findSampler(String name) {
        if (name == null) return null;
        GuiPackage gui = GuiPackage.getInstance();
        if (gui == null) return null;
        JMeterTreeModel model = gui.getTreeModel();
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        return findSamplerRec(root, name);
    }

    @SuppressWarnings("unchecked")
    private static JMeterTreeNode findSamplerRec(JMeterTreeNode node, String name) {
        TestElement te = node.getTestElement();
        if (te instanceof Sampler && node.getName().equalsIgnoreCase(name)) {
            return node;
        }
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            JMeterTreeNode result = findSamplerRec(child, name);
            if (result != null) return result;
        }
        return null;
    }

    /** Find all sampler names that contain the given substring (case-insensitive). */
    public static List<String> findSamplerNamesMatching(String substring) {
        List<String> out = new ArrayList<>();
        GuiPackage gui = GuiPackage.getInstance();
        if (gui == null || substring == null) return out;
        JMeterTreeModel model = gui.getTreeModel();
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        collectSamplerNames(root, substring.toLowerCase(), out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectSamplerNames(JMeterTreeNode node, String needleLower, List<String> out) {
        TestElement te = node.getTestElement();
        if (te instanceof Sampler) {
            String name = node.getName();
            if (name != null && (needleLower.isEmpty() || name.toLowerCase().contains(needleLower))) {
                out.add(name);
            }
        }
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            collectSamplerNames((JMeterTreeNode) children.nextElement(), needleLower, out);
        }
    }

    /** List all sampler names in the current tree. */
    public static List<String> getAllSamplerNames() {
        return findSamplerNamesMatching("");
    }

    /**
     * Duplicate detection. Returns true if a child of `samplerName` is an extractor / processor
     * of the same type referencing the same variable name (or producing similar effect).
     */
    public static boolean extractorExists(String samplerName, String varName, ExtractorKind kind) {
        if (samplerName == null || varName == null) return false;
        JMeterTreeNode sampler = findSampler(samplerName);
        if (sampler == null) return false;
        return childMatching(sampler, varName, kind) != null;
    }

    /** Returns the display name of the existing duplicate, or null. */
    public static String existingExtractorName(String samplerName, String varName, ExtractorKind kind) {
        if (samplerName == null || varName == null) return null;
        JMeterTreeNode sampler = findSampler(samplerName);
        if (sampler == null) return null;
        JMeterTreeNode dup = childMatching(sampler, varName, kind);
        return dup != null ? dup.getName() : null;
    }

    @SuppressWarnings("unchecked")
    private static JMeterTreeNode childMatching(JMeterTreeNode parent, String varName, ExtractorKind kind) {
        Enumeration<?> children = parent.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            TestElement te = child.getTestElement();
            switch (kind) {
                case REGEX -> {
                    if (te instanceof RegexExtractor re && varName.equals(re.getRefName())) return child;
                }
                case JSON_PATH -> {
                    if (te instanceof JSONPostProcessor jp) {
                        String refs = jp.getRefNames();
                        if (refs != null && containsToken(refs, varName)) return child;
                    }
                }
                case BOUNDARY -> {
                    if (te instanceof BoundaryExtractor be && varName.equals(be.getRefName())) return child;
                }
                case JSR223_PRE -> {
                    if (te instanceof JSR223PreProcessor && child.getName().equals(varName)) return child;
                }
                case JSR223_POST -> {
                    if (te instanceof JSR223PostProcessor && child.getName().equals(varName)) return child;
                }
                case TIMER -> {
                    if (te instanceof ConstantTimer) return child;
                }
                case ASSERTION -> {
                    if (te instanceof ResponseAssertion ra) {
                        try {
                            JMeterProperty p = ra.getTestStrings();
                            if (p != null && p.getStringValue().contains(varName)) return child;
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return null;
    }

    private static boolean containsToken(String csv, String token) {
        for (String part : csv.split("[,;\\s]+")) if (part.equals(token)) return true;
        return false;
    }

    public static boolean isJMeterGuiAvailable() {
        return GuiPackage.getInstance() != null;
    }

    public enum ExtractorKind { REGEX, JSON_PATH, BOUNDARY, JSR223_PRE, JSR223_POST, TIMER, ASSERTION }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private interface BoolTask { boolean run(); }
    private static boolean onEDT(BoolTask t) {
        if (SwingUtilities.isEventDispatchThread()) return t.run();
        final boolean[] result = {false};
        try {
            SwingUtilities.invokeAndWait(() -> result[0] = t.run());
        } catch (Exception e) {
            log.error("EDT execution failed", e);
        }
        return result[0];
    }
}
