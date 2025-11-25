package com.knowledge.robot;

import com.formdev.flatlaf.FlatLightLaf;
import com.knowledge.robot.ui.KnowledgeRobotApp;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            KnowledgeRobotApp app = new KnowledgeRobotApp();
            app.setVisible(true);
        });
    }
}
