package com.knowledge.robot.ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 星际科技风深色主题主窗口：
 * 只保留“智能点检”界面，
 * 全局深色 + 霓虹蓝高亮，
 * 日志栏单独做“监控终端”风格的层次配色。
 *
 * 约定：SmartInspectionPanel 中的日志文本组件需设置 name：
 *   logArea.setName("logArea");
 */
public class KnowledgeRobotApp extends JFrame {

    // ====== 全局科技感配色 ======
    // 整体背景：近黑深蓝
    private static final Color BG_MAIN  = new Color(10, 12, 20);
    // 内部面板：略亮一点
    private static final Color BG_PANEL = new Color(18, 22, 35);
    // 一般输入/文本区域背景
    private static final Color BG_INPUT = new Color(22, 28, 45);

    // 主文字 / 次文字
    private static final Color TEXT_MAIN  = new Color(220, 230, 245);
    private static final Color TEXT_MUTED = new Color(130, 145, 170);

    // 霓虹高亮蓝
    private static final Color ACCENT      = new Color(0, 174, 255);
    private static final Color ACCENT_SOFT = new Color(70, 190, 255);

    // ====== 日志栏专用配色（制造层次感） ======
    // 滚动区外圈背景（比内面板略深一些）
    private static final Color LOG_BG_OUTER = new Color(8, 14, 28);
    // 日志文本区背景（比普通输入略亮）
    private static final Color LOG_BG_INNER = new Color(16, 26, 48);
    // 日志文字颜色：偏蓝的亮白
    private static final Color LOG_TEXT     = new Color(192, 230, 255);

    // 仅保留智能点检主面板
    private final SmartInspectionPanel inspectionPanel = new SmartInspectionPanel();

    public KnowledgeRobotApp() {
        super("四川电信智能体开发平台-乐山群聊助手智能点检服务");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);

        buildUI();
        applySciFiTheme();

        // 关闭窗口时，顺便停掉后台任务
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                inspectionPanel.stopService();
            }
        });

        // 显示时让面板做自己的初始化（重置控件等）
        SwingUtilities.invokeLater(inspectionPanel::onShow);
    }

    /** 只展示智能点检面板，不再有配置/自动对话标签页 */
    private void buildUI() {
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(inspectionPanel, BorderLayout.CENTER);
    }

    // ===================== 科技感主题应用 =====================

    private void applySciFiTheme() {
        // 整个窗口外圈来一圈电光蓝边框
        getRootPane().setBorder(new MatteBorder(1, 1, 1, 1, ACCENT));
        getContentPane().setBackground(BG_MAIN);

        // 递归应用到所有子组件
        applySciFiToContainer(getContentPane());
    }

    /**
     * 遍历所有组件，根据类型应用深色 + 霓虹蓝风格。
     */
    private void applySciFiToContainer(Container container) {
        container.setBackground(BG_PANEL);

        for (Component comp : container.getComponents()) {

            // 面板：深色背景 + 标题边框调成蓝色
            if (comp instanceof JPanel panel) {
                panel.setOpaque(true);
                panel.setBackground(BG_PANEL);
                Border b = panel.getBorder();
                if (b instanceof TitledBorder tb) {
                    tb.setTitleColor(ACCENT_SOFT);
                    tb.setBorder(new LineBorder(ACCENT, 1, true));
                }
            }

            // 文本组件统一交给 styleTextComponent
            if (comp instanceof JTextComponent tc) {
                styleTextComponent(tc);
            }

            // 标签文字颜色
            if (comp instanceof JLabel label) {
                label.setForeground(TEXT_MAIN);
            }

            // 按钮：电光蓝边框 + 深色按钮
            if (comp instanceof AbstractButton btn) {
                styleButton(btn);
            }

            // 滚动区域：普通和日志区分开处理
            if (comp instanceof JScrollPane sp) {
                sp.setBackground(BG_PANEL);
                Component view = sp.getViewport().getView();

                // ★ 日志栏滚动区：双层蓝边 + 深色外圈
                if (view instanceof JTextComponent tcView &&
                        "logArea".equals(tcView.getName())) {

                    sp.getViewport().setBackground(LOG_BG_OUTER);
                    sp.setBorder(new CompoundBorder(
                            new MatteBorder(1, 1, 1, 1,
                                    new Color(0, 160, 255)),   // 外圈电光蓝
                            new MatteBorder(1, 1, 1, 1,
                                    new Color(0, 35, 70))      // 内圈深蓝过渡
                    ));
                } else {
                    // 普通滚动区
                    sp.getViewport().setBackground(BG_INPUT);
                    sp.setBorder(new LineBorder(ACCENT, 1, true));
                }
            }

            // 表格：深色背景 + 蓝色网格 + 高亮表头
            if (comp instanceof JTable table) {
                table.setBackground(BG_INPUT);
                table.setForeground(TEXT_MAIN);
                table.setGridColor(new Color(50, 60, 90));
                table.setSelectionBackground(new Color(0, 90, 160));
                table.setSelectionForeground(TEXT_MAIN);
                if (table.getTableHeader() != null) {
                    table.getTableHeader().setBackground(new Color(15, 20, 32));
                    table.getTableHeader().setForeground(ACCENT_SOFT);
                    table.getTableHeader().setBorder(
                            new MatteBorder(0, 0, 1, 0, ACCENT)
                    );
                }
            }

            // 分割条：细蓝线分界
            if (comp instanceof JSplitPane split) {
                split.setBackground(BG_PANEL);
                split.setDividerSize(6);
                split.setBorder(new MatteBorder(
                        1, 0, 0, 0, new Color(30, 40, 60)
                ));
            }

            // 选项卡（如果 SmartInspectionPanel 里用到了）
            if (comp instanceof JTabbedPane tabs) {
                tabs.setBackground(BG_PANEL);
                tabs.setForeground(TEXT_MUTED);
                tabs.setBorder(new MatteBorder(1, 1, 0, 1, ACCENT));
            }

            // 数字选择器 / Spinner
            if (comp instanceof JSpinner spinner) {
                spinner.setBackground(BG_INPUT);
                spinner.setForeground(TEXT_MAIN);
                JComponent editor = spinner.getEditor();
                editor.setBackground(BG_INPUT);
                editor.setForeground(TEXT_MAIN);
            }

            // 递归处理所有子容器
            if (comp instanceof Container child) {
                applySciFiToContainer(child);
            }
        }
    }

    /**
     * 文本组件统一样式：
     * 其中 name="logArea" 的走日志专用样式。
     */
    private void styleTextComponent(JTextComponent tc) {
        // ★ 日志栏：终端风格单独处理
        if ("logArea".equals(tc.getName())) {
            styleLogConsole(tc);
            return;
        }

        // 普通输入框 / 文本框
        tc.setOpaque(true);
        tc.setBackground(BG_INPUT);
        tc.setForeground(TEXT_MAIN);
        tc.setCaretColor(ACCENT_SOFT);
        tc.setSelectionColor(new Color(0, 100, 180));
        tc.setBorder(new CompoundBorder(
                new LineBorder(new Color(0, 120, 200), 1, true),
                new EmptyBorder(3, 6, 3, 6)
        ));
    }

    /**
     * 日志栏专用样式：
     * 更亮的深蓝背景 + 偏蓝亮字 + 等宽字体 + 蓝色边框。
     */
    // 日志栏专用样式：支持中文的终端风
    private void styleLogConsole(JTextComponent tc) {
        tc.setOpaque(true);
        tc.setBackground(LOG_BG_INNER);
        tc.setForeground(LOG_TEXT);
        tc.setCaretColor(ACCENT_SOFT);
        tc.setSelectionColor(new Color(0, 90, 170));

        // ★ 使用支持中文的字体，而不是 Consolas
        //   Windows 下“微软雅黑”系列基本都在，够稳妥
        Font base = tc.getFont();
        Font cnFont = new Font("Microsoft YaHei UI", Font.PLAIN, base.getSize());
        if ("Microsoft YaHei UI".equals(cnFont.getFamily())) {
            tc.setFont(cnFont);
        } else {
            // 如果这字体不存在，就老老实实用默认字体
            tc.setFont(base.deriveFont(Font.PLAIN, base.getSize()));
        }

        tc.setBorder(new CompoundBorder(
                new LineBorder(new Color(0, 140, 255), 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    /** 按钮：深色背景 + 霓虹蓝边框 + 加粗字体 */
    private void styleButton(AbstractButton btn) {
        btn.setOpaque(true);
        btn.setBackground(new Color(20, 30, 50));
        btn.setForeground(ACCENT_SOFT);
        btn.setBorder(new CompoundBorder(
                new LineBorder(ACCENT, 1, true),
                new EmptyBorder(3, 10, 3, 10)
        ));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(true);

        Font f = btn.getFont();
        btn.setFont(f.deriveFont(Font.BOLD, f.getSize()));
    }

    public static void main(String[] args) {
        try {
            // 先用系统默认 LAF，再叠加我们自己的深色主题
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }

        SwingUtilities.invokeLater(() -> {
            KnowledgeRobotApp app = new KnowledgeRobotApp();
            app.setVisible(true);
        });
    }
}
