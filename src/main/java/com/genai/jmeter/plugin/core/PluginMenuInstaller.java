package com.genai.jmeter.plugin.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Registers the plugin menu item in JMeter's Tools menu via reflection-safe swing traversal.
 */
public class PluginMenuInstaller {

    private static final Logger log = LoggerFactory.getLogger(PluginMenuInstaller.class);
    private static boolean installed = false;

    public static synchronized void install() {
        if (installed) return;
        try {
            SwingUtilities.invokeLater(PluginMenuInstaller::tryInstallMenuitem);
            installed = true;
        } catch (Exception e) {
            log.warn("Menu installation scheduling failed: {}", e.getMessage());
        }
    }

    private static void tryInstallMenuitem() {
        try {
            // Find the active JFrame (JMeter main window)
            for (Frame frame : Frame.getFrames()) {
                if (frame instanceof JFrame jframe && frame.isVisible()) {
                    JMenuBar menuBar = jframe.getJMenuBar();
                    if (menuBar == null) continue;
                    JMenu toolsMenu = findMenu(menuBar, "Tools");
                    if (toolsMenu == null) continue;

                    // Guard: don't install twice
                    for (int i = 0; i < toolsMenu.getItemCount(); i++) {
                        JMenuItem item = toolsMenu.getItem(i);
                        if (item != null && "GenAI Correlation Plugin".equals(item.getText())) return;
                    }

                    toolsMenu.addSeparator();
                    JMenuItem item = new JMenuItem("GenAI Correlation Plugin");
                    item.setToolTipText("From Recording to Production-Ready Script");
                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G,
                            ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
                    item.addActionListener(e -> openPlugin());
                    toolsMenu.add(item);
                    log.info("GenAI Plugin menu item installed");
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("Could not install menu item: {}", e.getMessage());
        }
    }

    private static JMenu findMenu(JMenuBar menuBar, String name) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && name.equals(menu.getText())) return menu;
        }
        return null;
    }

    private static void openPlugin() {
        try {
            org.apache.jmeter.gui.action.ActionRouter.getInstance()
                    .doActionNow(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED,
                            GenAIPluginCommand.ACTION_NAME));
        } catch (Exception e) {
            log.error("Failed to open GenAI Plugin: {}", e.getMessage(), e);
            // Fallback: open directly
            new com.genai.jmeter.plugin.ui.MainFrame().setVisible(true);
        }
    }
}
