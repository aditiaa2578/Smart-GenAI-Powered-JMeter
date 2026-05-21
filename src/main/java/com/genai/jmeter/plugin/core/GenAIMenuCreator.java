package com.genai.jmeter.plugin.core;

import org.apache.jmeter.gui.plugin.MenuCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Registers "GenAI Correlation Plugin" in JMeter's Tools menu.
 * Discovered via ServiceLoader: META-INF/services/org.apache.jmeter.gui.plugin.MenuCreator
 *
 * Note: JMeter 5.x MenuCreator uses arrays (JMenuItem[], JMenu[]) not List.
 */
public class GenAIMenuCreator implements MenuCreator {

    private static final Logger log = LoggerFactory.getLogger(GenAIMenuCreator.class);

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location == MENU_LOCATION.TOOLS) {
            JMenuItem item = new JMenuItem("GenAI Correlation Plugin");
            item.setToolTipText("From Recording to Production-Ready Script — AI-powered HAR correlation");
            item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
            item.addActionListener(e -> openPlugin(e));
            return new JMenuItem[]{item};
        }
        return new JMenuItem[0];
    }

    private void openPlugin(ActionEvent e) {
        SwingUtilities.invokeLater(() -> {
            try {
                org.apache.jmeter.gui.action.ActionRouter.getInstance()
                        .doActionNow(new ActionEvent(e.getSource(),
                                ActionEvent.ACTION_PERFORMED,
                                GenAIPluginCommand.ACTION_NAME));
            } catch (Exception ex) {
                log.warn("ActionRouter failed, opening directly: {}", ex.getMessage());
                new com.genai.jmeter.plugin.ui.MainFrame().setVisible(true);
            }
        });
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }

    @Override
    public void localeChanged() {
        // no locale-sensitive strings
    }
}
