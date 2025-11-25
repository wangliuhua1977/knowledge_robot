package com.knowledge.robot.ui;

import javax.swing.*;
import java.awt.*;

/**
 * 保留兼容性：使用新的 SmartInspectionPanel 实现智能点检。
 * 默认主界面采用卡片切换，此对话框仅供需要独立窗口时复用面板逻辑。
 */
public class SmartInspectionDialog extends JDialog {
    private final SmartInspectionPanel panel = new SmartInspectionPanel();

    public SmartInspectionDialog(Frame owner) {
        super(owner, "智能点检", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(panel);
        setPreferredSize(new Dimension(900, 640));
        pack();
        setLocationRelativeTo(owner);
    }

    @Override
    public void setVisible(boolean b) {
        if (b) {
            panel.onShow();
        }
        super.setVisible(b);
    }

    public void stopService() {
        panel.stopService();
    }
}
