package com.knowledge.robot.ui;

import com.knowledge.robot.service.AutoChatService;
import com.knowledge.robot.service.StatsStore;
import com.knowledge.robot.util.QuestionBank;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class KnowledgeRobotApp extends JFrame {
    private static final String PREF_NODE = "com.knowledge.robot.ui.KnowledgeRobotApp";
    private static final String KEY_SELECTED_CATS = "selected_categories";
    private static final String KEY_RANDOM = "random_interval";
    private static final String KEY_MAX_SEC = "max_interval";
    private static final String KEY_THEME = "theme";
    private static final String SEP = "\u001F"; // 文件不可见分隔符

    // 样式：亮蓝色（问题行）
    private static final Color BRIGHT_BLUE = new Color(84, 195, 230); // #007AFF
    // 样式：思考栏暗色
    private static final Color THINK_DARK = new Color(170, 170, 170);

    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final JPanel nav = new JPanel();
    private final SmartInspectionPanel inspectionPanel = new SmartInspectionPanel();
    private final JComboBox<String> themeCombo = new JComboBox<>();
    private final JLabel themeLabel = new JLabel("配色主题");
    private final JButton navToConfig = new JButton("配置");
    private final JButton navToInspectionTab = new JButton("智能点检");

    // Config panel widgets
    private final JPanel configPanel = new JPanel(new BorderLayout());
    private final JPanel categoryPanel = new JPanel(new GridLayout(0, 3, 8, 8));
    private final JSlider maxIntervalSlider = new JSlider(1, 1800, 10);
    private final JLabel maxIntervalLabel = new JLabel("最大间隔秒数: 10");
    private final JCheckBox randomIntervalCheck = new JCheckBox("随机间隔 (1 ~ 最大间隔)", true);
    private final JButton btnSelectAll = new JButton("全选");
    private final JButton btnDeselectAll = new JButton("全部取消");
    private final JButton saveConfigBtn = new JButton("保存设置");
    private final JButton toInspection = new JButton("智能点检");

    // Auto chat panel widgets
    private final JPanel autoPanel = new JPanel(new BorderLayout());
    private final JTextArea thinkArea = new JTextArea();               // 思考栏（暗色 + 斜体）
    private final JTextPane convoPane = new JTextPane();               // 对话过程（问题亮蓝）
    private final JButton startBtn = new JButton("开始自动对话");
    private final JButton stopBtn = new JButton("停止");
    private final JLabel runCountLabel = new JLabel("累计发起对话：0");
    private final JLabel nextInLabel = new JLabel("下次对话倒计时：—");

    // 自定义问题
    private final JTextField customField = new JTextField();
    private final JButton sendBtn = new JButton("发送");

    private final List<JCheckBox> categoryChecks = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private AutoChatService service;

    public KnowledgeRobotApp() {
        super("四川电信智能体开发平台-乐山群聊助手智能点检服务");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);
        initThemeCombo();
        buildUI();
        bindActions();
        loadPreferencesAndApply();        // 启动时恢复上次选择（首次默认全选）
        refreshRunCount();
        SwingUtilities.invokeLater(() -> {
            inspectionPanel.onShow();
            cardLayout.show(cardPanel, "inspection");
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                inspectionPanel.stopService();
            }
        });
    }

    private void buildUI() {
        // Left navigation
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        nav.add(navToConfig);
        nav.add(Box.createVerticalStrut(10));
        nav.add(navToInspectionTab);
        nav.add(Box.createVerticalStrut(15));
        nav.add(themeLabel);
        nav.add(themeCombo);
        nav.add(Box.createVerticalGlue());

        // Config panel content
        JPanel cfgTop = new JPanel(new BorderLayout());
        cfgTop.setBorder(new TitledBorder("兴趣分类（多选）"));

        for (String cat : QuestionBank.categories()) {
            JCheckBox cb = new JCheckBox(cat, true); // 首次默认全选，之后由 loadPreferences 覆盖
            categoryChecks.add(cb);
            categoryPanel.add(cb);
        }
        JScrollPane catScroll = new JScrollPane(categoryPanel);
        cfgTop.add(catScroll, BorderLayout.CENTER);

        JPanel cfgBottom = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6,6,6,6);
        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;
        cfgBottom.add(new JLabel("自动对话间隔"), gc);
        gc.gridx = 1;
        maxIntervalSlider.addChangeListener(e -> maxIntervalLabel.setText("最大间隔秒数: " + maxIntervalSlider.getValue()));
        cfgBottom.add(maxIntervalSlider, gc);
        gc.gridx = 2;
        cfgBottom.add(maxIntervalLabel, gc);

        gc.gridx = 0; gc.gridy = 1; gc.gridwidth = 1;
        cfgBottom.add(randomIntervalCheck, gc);
        gc.gridx = 1;
        cfgBottom.add(btnSelectAll, gc);
        gc.gridx = 2;
        cfgBottom.add(btnDeselectAll, gc);

        gc.gridx = 2; gc.gridy = 2; gc.gridwidth = 1; gc.anchor = GridBagConstraints.EAST;
        cfgBottom.add(saveConfigBtn, gc);

        JPanel cfgContainer = new JPanel(new BorderLayout(10,10));
        cfgContainer.add(cfgTop, BorderLayout.CENTER);
        cfgContainer.add(cfgBottom, BorderLayout.SOUTH);
        configPanel.add(cfgContainer, BorderLayout.CENTER);

        // Auto chat panel content
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(startBtn);
        topBar.add(stopBtn);
        stopBtn.setEnabled(false);
        topBar.add(runCountLabel);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(nextInLabel);

        // 思考栏：暗色 + 斜体
        thinkArea.setEditable(false);
        thinkArea.setLineWrap(true);
        thinkArea.setWrapStyleWord(true);
        thinkArea.setForeground(THINK_DARK);
        thinkArea.setFont(thinkArea.getFont().deriveFont(Font.ITALIC));

        // 对话区（Styled）
        convoPane.setEditable(false);
        convoPane.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        JScrollPane thinkScroll = new JScrollPane(thinkArea);
        thinkScroll.setBorder(new TitledBorder("思维链"));
        JScrollPane convoScroll = new JScrollPane(convoPane);
        convoScroll.setBorder(new TitledBorder("交流栏"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, convoScroll, thinkScroll);
        split.setDividerLocation(360);
        autoPanel.add(topBar, BorderLayout.NORTH);
        autoPanel.add(split, BorderLayout.CENTER);

        // 底部：自定义问题输入
        JPanel bottomBar = new JPanel(new BorderLayout(8, 8));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        bottomBar.add(customField, BorderLayout.CENTER);
        bottomBar.add(sendBtn, BorderLayout.EAST);
        autoPanel.add(bottomBar, BorderLayout.SOUTH);

        // Cards
        cardPanel.add(configPanel, "cfg");
        cardPanel.add(autoPanel, "auto");
        cardPanel.add(inspectionPanel, "inspection");

        // Root
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(nav, BorderLayout.WEST);
        getContentPane().add(cardPanel, BorderLayout.CENTER);

        navToConfig.addActionListener(e -> cardLayout.show(cardPanel, "cfg"));
        navToInspectionTab.addActionListener(e -> {
            inspectionPanel.onShow();
            cardLayout.show(cardPanel, "inspection");
        });

        applyTheme(ThemePalette.DEFAULT);
    }

    private void bindActions() {
        saveConfigBtn.addActionListener(this::onSave);
        startBtn.addActionListener(this::onStart);
        stopBtn.addActionListener(this::onStop);
        btnSelectAll.addActionListener(e -> setAllCategoriesChecked(true));
        btnDeselectAll.addActionListener(e -> setAllCategoriesChecked(false));

        // 发送自定义问题：按钮/回车
        sendBtn.addActionListener(this::onSendCustom);
        customField.addActionListener(this::onSendCustom);
        InputMap im = customField.getInputMap(JComponent.WHEN_FOCUSED);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "SEND");
        customField.getActionMap().put("SEND", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onSendCustom(e); }
        });
    }

    /* ====================== 事件处理 ====================== */

    private void onSave(ActionEvent e) {
        if (getSelectedCategoriesFromUI().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请至少选择一个对话分类。");
            return;
        }
        persistPreferences();
        JOptionPane.showMessageDialog(this, "配置已保存。下次启动将自动恢复。");
    }

    private void onStart(ActionEvent e) {
        if (getSelectedCategoriesFromUI().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请至少选择一个对话分类。");
            return;
        }
        running.set(true);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        // 新一轮开始：清空可视区
        thinkArea.setText("");
        setConvoText("");

        ensureService();
        service.start(running);
    }

    private void onStop(ActionEvent e) {
        running.set(false);
        if (service != null) service.stop();
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        nextInLabel.setText("下次对话倒计时：—");
    }

    private void initThemeCombo() {
        themeCombo.removeAllItems();
        for (ThemePalette palette : ThemePalette.values()) {
            themeCombo.addItem(palette.displayName());
        }
        themeCombo.addActionListener(e -> {
            ThemePalette palette = ThemePalette.fromDisplayName((String) themeCombo.getSelectedItem());
            prefs.put(KEY_THEME, palette.name());
            applyTheme(palette);
        });
    }

    private void applyTheme(ThemePalette palette) {
        getContentPane().setBackground(palette.background());
        cardPanel.setBackground(palette.panel());
        nav.setBackground(palette.panel());
        configPanel.setBackground(palette.panel());
        categoryPanel.setBackground(palette.panel());
        autoPanel.setBackground(palette.panel());
        thinkArea.setBackground(palette.panel());
        thinkArea.setForeground(palette.text());
        convoPane.setBackground(palette.panel());
        convoPane.setForeground(palette.text());
        customField.setBackground(palette.panel());
        customField.setForeground(palette.text());
        runCountLabel.setForeground(palette.text());
        nextInLabel.setForeground(palette.text());
        themeLabel.setForeground(palette.text());
        themeCombo.setBackground(palette.panel());
        themeCombo.setForeground(palette.text());

        updateContainerColors(configPanel, palette);
        updateContainerColors(autoPanel, palette);
        updateContainerColors(nav, palette);

        styleButtons(palette, startBtn, stopBtn, btnSelectAll, btnDeselectAll, saveConfigBtn,
                toInspection, sendBtn, navToConfig, navToInspectionTab);
        inspectionPanel.applyTheme(palette);
        repaint();
    }

    private void updateContainerColors(Container container, ThemePalette palette) {
        container.setBackground(palette.panel());
        for (Component component : container.getComponents()) {
            if (component instanceof JScrollPane scrollPane) {
                scrollPane.setBackground(palette.panel());
                scrollPane.getViewport().setBackground(palette.panel());
            }
            if (component instanceof JSplitPane splitPane) {
                splitPane.setBackground(palette.panel());
            }
            if (component instanceof JLabel label) {
                label.setForeground(palette.text());
            }
            if (component instanceof JTable table) {
                table.setBackground(palette.panel());
                table.setForeground(palette.text());
                if (table.getTableHeader() != null) {
                    table.getTableHeader().setBackground(palette.background());
                    table.getTableHeader().setForeground(palette.text());
                }
            }
            if (component instanceof JTextComponent textComponent) {
                textComponent.setBackground(palette.panel());
                textComponent.setForeground(palette.text());
                textComponent.setCaretColor(palette.text());
                textComponent.setSelectionColor(palette.accent());
            }
            if (component instanceof JCheckBox cb) {
                cb.setBackground(palette.panel());
                cb.setForeground(palette.text());
            }
            if (component instanceof JSpinner spinner) {
                spinner.setBackground(palette.panel());
                spinner.setForeground(palette.text());
            }
            if (component instanceof Container cont) {
                updateContainerColors(cont, palette);
            }
        }
    }

    private void styleButtons(ThemePalette palette, AbstractButton... buttons) {
        for (AbstractButton button : buttons) {
            if (button == null) continue;
            button.setBackground(palette.accent());
            button.setForeground(palette.accentText());
            button.setOpaque(true);
            button.setBorder(BorderFactory.createLineBorder(palette.accent().darker()));
        }
    }

    private void onSendCustom(ActionEvent e) {
        String q = customField.getText().trim();
        if (q.isEmpty()) return;

        ensureService();
        // 先把“问题”行写入对话区（加粗+加大+亮蓝）
      //  appendQuestionLine(q);
        // 交给服务跑一轮（不会触发自动调度）
        service.askOnce(q);

        customField.setText("");
        customField.requestFocus();
    }

    private void openInspection() {
        SmartInspectionDialog dialog = new SmartInspectionDialog(this);
        dialog.setVisible(true);
    }

    /* ====================== 与 Service 的衔接 ====================== */

    /** 确保 service 可用；未启动自动也能手工问 */
    private void ensureService() {
        if (service != null) return;
        service = new AutoChatService(
                this::getSelectedCategoriesFromUI,
                () -> randomIntervalCheck.isSelected(),
                () -> maxIntervalSlider.getValue(),
                this::appendConvoStream,
                this::appendThinkStream,
                this::onOneDialogFinished,
                this::updateCountdownLabel
        );
    }

    private void appendConvoStream(String text) {
        SwingUtilities.invokeLater(() -> {
            if ("-------".equals(text.trim())) {
                appendSeparator();
            } else if (text.startsWith("我: ")) {
                appendQuestionLine(text.substring("我: ".length()));
            } else if (text.startsWith("助手: ")) {
                appendAssistantBlock(text.substring("助手: ".length()));
            } else {
                appendNormal(text + "\n");
            }
        });
    }

    private void appendThinkStream(String text) {
        SwingUtilities.invokeLater(() -> {
            if (text.contains("<<CLEAR_THOUGHTS>>")) {
                thinkArea.setText("");
                thinkArea.setCaretPosition(0);
            } else {
                thinkArea.append(text); // 流式、无时间戳、中文段落样式
                thinkArea.setCaretPosition(thinkArea.getDocument().getLength());
            }
        });
    }

    private void onOneDialogFinished() {
        StatsStore.increment();
        refreshRunCount();
    }

    private void refreshRunCount() {
        runCountLabel.setText("累计发起对话：" + StatsStore.read());
    }

    private void updateCountdownLabel(int seconds) {
        SwingUtilities.invokeLater(() -> nextInLabel.setText("下次对话倒计时：" + seconds + " s"));
    }

    /* ====================== 对话区样式辅助 ====================== */

    private void setConvoText(String text) {
        convoPane.setText(text);
    }

    private void appendQuestionLine(String question) {
        int baseSize = convoPane.getFont().getSize();
        appendStyled("我: " + question + "\n", true, baseSize + 1, BRIGHT_BLUE);
    }

    private void appendAssistantBlock(String content) {
        appendStyled("助手: ", true, convoPane.getFont().getSize(), null); // 前缀加粗
        appendStyled(content + "\n", false, convoPane.getFont().getSize(), null);
    }

    private void appendSeparator() {
        appendStyled("-------\n", false, convoPane.getFont().getSize(), Color.GRAY);
    }

    private void appendNormal(String text) {
        appendStyled(text, false, convoPane.getFont().getSize(), null);
    }

    private void appendStyled(String text, boolean bold, int fontSize, Color color) {
        StyledDocument doc = convoPane.getStyledDocument();
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setBold(attrs, bold);
        StyleConstants.setFontSize(attrs, fontSize);
        if (color != null) StyleConstants.setForeground(attrs, color);
        try {
            doc.insertString(doc.getLength(), text, attrs);
            convoPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    /* ====================== 偏好设置（持久化） ====================== */

    private void persistPreferences() {
        // 保存分类
        String catsJoined = getSelectedCategoriesFromUI().stream().collect(Collectors.joining(SEP));
        prefs.put(KEY_SELECTED_CATS, catsJoined);
        // 保存随机与间隔
        prefs.putBoolean(KEY_RANDOM, randomIntervalCheck.isSelected());
        prefs.putInt(KEY_MAX_SEC, maxIntervalSlider.getValue());
    }

    private void loadPreferencesAndApply() {
        // 分类
        String saved = prefs.get(KEY_SELECTED_CATS, null);
        if (saved == null || saved.isBlank()) {
            setAllCategoriesChecked(true); // 首次默认全选
        } else {
            Set<String> want = new LinkedHashSet<>(Arrays.asList(saved.split(SEP, -1)));
            for (JCheckBox cb : categoryChecks) {
                cb.setSelected(want.contains(cb.getText()));
            }
        }
        // 随机与间隔
        randomIntervalCheck.setSelected(prefs.getBoolean(KEY_RANDOM, true));
        int max = prefs.getInt(KEY_MAX_SEC, 10);
        max = Math.max(1, Math.min(1800, max));
        maxIntervalSlider.setValue(max);
        maxIntervalLabel.setText("最大间隔秒数: " + maxIntervalSlider.getValue());

        String themeName = prefs.get(KEY_THEME, ThemePalette.DEFAULT.name());
        ThemePalette palette = ThemePalette.valueOf(themeName);
        themeCombo.setSelectedItem(palette.displayName());
        applyTheme(palette);
    }

    private void setAllCategoriesChecked(boolean checked) {
        for (JCheckBox cb : categoryChecks) cb.setSelected(checked);
    }

    private List<String> getSelectedCategoriesFromUI() {
        List<String> chosen = new ArrayList<>();
        for (JCheckBox cb : categoryChecks) if (cb.isSelected()) chosen.add(cb.getText());
        if (chosen.isEmpty()) {
            chosen.addAll(QuestionBank.categories());
        }
        return chosen;
    }
}
