package com.knowledge.robot;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.knowledge.robot.ui.KnowledgeRobotApp;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                FlatMacDarkLaf.setup();
            } catch (Exception ignore) {}
            new KnowledgeRobotApp().setVisible(true);
        });
    }
}
