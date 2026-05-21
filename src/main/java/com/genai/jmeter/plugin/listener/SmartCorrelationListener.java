package com.genai.jmeter.plugin.listener;

import com.genai.jmeter.plugin.listener.ui.SmartListenerPanel;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.visualizers.gui.AbstractVisualizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * JMeter Listener tree element: Add > Listeners > GenAI Smart Correlation Listener
 *
 * Captures all sample results during a test run and feeds them into the
 * SmartListenerPanel where users can inspect, extract, and AI-correlate.
 */
public class SmartCorrelationListener extends AbstractVisualizer implements TestStateListener {

    private static final Logger log = LoggerFactory.getLogger(SmartCorrelationListener.class);
    private static final long serialVersionUID = 1L;

    public static final String LABEL = "GenAI Smart Correlation Listener";
    public static final String GUI_CLASS = "com.genai.jmeter.plugin.listener.SmartCorrelationListener";

    private final SmartListenerPanel panel;

    public SmartCorrelationListener() {
        panel = new SmartListenerPanel();
        setLayout(new java.awt.BorderLayout());
        add(panel);
    }

    @Override
    public String getStaticLabel() {
        return LABEL;
    }

    @Override
    public String getLabelResource() {
        return getClass().getSimpleName();
    }

    /**
     * Called by JMeter for every sample result during test execution.
     */
    @Override
    public void add(SampleResult result) {
        try {
            panel.addResult(result);
        } catch (Exception e) {
            log.error("Error adding sample result to SmartCorrelationListener", e);
        }
    }

    @Override
    public void clearData() {
        panel.clearResults();
    }

    // ── TestStateListener callbacks ───────────────────────────────────────────

    @Override
    public void testStarted() {
        SwingUtilities.invokeLater(() -> {
            panel.clearResults();
            log.info("SmartCorrelationListener: test started, results cleared");
        });
    }

    @Override
    public void testStarted(String host) {
        testStarted();
    }

    @Override
    public void testEnded() {
        log.info("SmartCorrelationListener: test ended, {} results captured",
                panel.getResults().size());
    }

    @Override
    public void testEnded(String host) {
        testEnded();
    }

    public SmartListenerPanel getSmartPanel() {
        return panel;
    }
}
