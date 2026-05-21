package com.genai.jmeter.plugin.core;

import com.genai.jmeter.plugin.ui.MainFrame;
import org.apache.jmeter.gui.action.AbstractAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * JMeter Command that registers the GenAI Plugin under the Tools menu.
 * Discovered automatically by JMeter via classpath scanning of AbstractAction subclasses.
 */
public class GenAIPluginCommand extends AbstractAction {

    private static final Logger log = LoggerFactory.getLogger(GenAIPluginCommand.class);
    public static final String ACTION_NAME = "genai_plugin_open";

    private static MainFrame mainFrame;

    @Override
    public void doAction(ActionEvent e) {
        SwingUtilities.invokeLater(() -> {
            if (mainFrame == null || !mainFrame.isDisplayable()) {
                mainFrame = new MainFrame();
            }
            mainFrame.setVisible(true);
            mainFrame.toFront();
            mainFrame.requestFocus();
        });
    }

    @Override
    public Set<String> getActionNames() {
        Set<String> actions = new HashSet<>();
        actions.add(ACTION_NAME);
        return actions;
    }
}
