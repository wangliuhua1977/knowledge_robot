package com.knowledge.robot.ui;

import com.knowledge.robot.inspection.SmartInspectionConfig;
import com.knowledge.robot.inspection.SmartInspectionLogger;
import com.knowledge.robot.inspection.SmartInspectionService;
import com.knowledge.robot.util.AppSettings;
import com.knowledge.robot.ui.ThemePalette;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.prefs.Preferences;

public class SmartInspectionPanel extends JPanel implements SmartInspectionLogger {
    private static final String PREF_NODE = "com.knowledge.robot.ui.SmartInspection";
    private static final String KEY_FOLDER = "inspection_folder";
    private static final String KEY_INTERVAL = "inspection_interval";

    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    private final JTextField folderField = new JTextField();
    // 间隔(秒) 选择框：5 位数字宽度
    private final JSpinner intervalSpinner =
            new JSpinner(new SpinnerNumberModel(60, 5, 3600, 5));
    private final int intervalColumns = 5;   // ★ 显示 5 个数字宽度

    private final JButton startBtn = new JButton("启动任务");
    private final JButton stopBtn = new JButton("停止");

    // 日志改成 JTextPane，支持按内容着色
    private final JTextPane processLogArea = new JTextPane();

    private final HistoryTableModel historyTableModel = new HistoryTableModel();
    private final JTable historyTable = new JTable(historyTableModel);
    private final JSpinner fromDateSpinner;
    private final JSpinner toDateSpinner;
    private final JSpinner daySpinner;
    private final JRadioButton groupByDay = new JRadioButton("按日期", true);
    private final JRadioButton groupByRange = new JRadioButton("按时段");
    private final JPanel params = new JPanel(new GridBagLayout());
    private final JPanel historyPanel = new JPanel(new BorderLayout());

    private SmartInspectionService service;

    // 日志时间戳格式
    private final SimpleDateFormat logTsFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 日志配色（跟整体深色+蓝光风格协调）
    private static final Color LOG_TS_COLOR      = new Color(140, 155, 185); // 时间戳
    private static final Color LOG_NORMAL_COLOR  = new Color(192, 230, 255); // 普通信息
    private static final Color LOG_HEADER_COLOR  = new Color(0, 200, 255);   // 分隔/启动/停止
    private static final Color LOG_SUCCESS_COLOR = new Color(0, 210, 150);   // 成功类
    private static final Color LOG_WARN_COLOR    = new Color(255, 205, 130); // （预留）警告
    private static final Color LOG_ERROR_COLOR   = new Color(255, 140, 140); // 错误

    public SmartInspectionPanel() {
        this.fromDateSpinner = createDateSpinner();
        this.toDateSpinner = createDateSpinner();
        this.daySpinner = createDateSpinner();
        setLayout(new BorderLayout(8, 8));
        buildUI();
        bindActions();
        loadPrefs();
    }

    private void buildUI() {
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // ===== 第 1 行：扫描目录 =====
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0;
        params.add(new JLabel("扫描目录"), gc);

        gc.gridx = 1;
        gc.weightx = 1;   // 只有目录文本框占满一行
        params.add(folderField, gc);

        JButton browse = new JButton("选择");
        gc.gridx = 2;
        gc.weightx = 0;
        params.add(browse, gc);
        browse.addActionListener(e -> chooseFolder());

        // ===== 第 2 行：间隔(秒) =====
        gc.gridy = 1;
        gc.gridx = 0;
        gc.weightx = 0;
        params.add(new JLabel("间隔(秒)"), gc);

        gc.gridx = 1;
        gc.weightx = 0;   // ★ 不再拉伸整行，按 preferredSize 显示
        params.add(intervalSpinner, gc);

        // 设置编辑框显示为 5 个数字宽度
        JSpinner.NumberEditor intervalEditor =
                (JSpinner.NumberEditor) intervalSpinner.getEditor();
        intervalEditor.getTextField().setColumns(intervalColumns);

        // 顶部按钮区
        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topButtons.add(startBtn);
        topButtons.add(stopBtn);
        stopBtn.setEnabled(false);

        JPanel paramBorder = new JPanel(new BorderLayout());
        paramBorder.setBorder(new TitledBorder("任务参数设置"));
        paramBorder.add(params, BorderLayout.CENTER);
        paramBorder.add(topButtons, BorderLayout.SOUTH);

        // 日志区域：只读 + 命名为 logArea 便于外层主题识别
        processLogArea.setEditable(false);
        processLogArea.setName("logArea");

        JScrollPane processLogScroll = new JScrollPane(processLogArea);
        processLogScroll.setBorder(new TitledBorder("处理日志"));

        // 历史过滤区
        JPanel historyFilter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        ButtonGroup group = new ButtonGroup();
        group.add(groupByDay);
        group.add(groupByRange);

        historyFilter.add(groupByDay);
        historyFilter.add(daySpinner);
        historyFilter.add(groupByRange);
        historyFilter.add(fromDateSpinner);
        historyFilter.add(toDateSpinner);
        JButton refreshHistory = new JButton("刷新历史");
        historyFilter.add(refreshHistory);
        refreshHistory.addActionListener(e -> refreshHistory());

        // 历史表格
        historyTable.setRowHeight(80);
        historyTable.setAutoCreateRowSorter(true);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.setShowGrid(true);
        historyTable.setGridColor(Color.LIGHT_GRAY);
        historyTable.setIntercellSpacing(new Dimension(1, 1));

        JScrollPane historyScroll = new JScrollPane(historyTable);
        historyScroll.setBorder(new TitledBorder("历史处理记录"));
        historyPanel.add(historyFilter, BorderLayout.NORTH);
        historyPanel.add(historyScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, processLogScroll, historyPanel);
        split.setDividerLocation(260);

        add(paramBorder, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private void bindActions() {
        startBtn.addActionListener(e -> onStart());
        stopBtn.addActionListener(e -> onStop());
        groupByDay.addActionListener(e -> toggleFilterMode());
        groupByRange.addActionListener(e -> toggleFilterMode());
        historyTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedImage();
                }
            }
        });
    }

    /** 旧主题接口，保留以兼容；深色科幻主题由 KnowledgeRobotApp 统一处理 */
    public void applyTheme(ThemePalette palette) {
        setBackground(palette.panel());
        params.setBackground(palette.panel());
        historyPanel.setBackground(palette.panel());

        folderField.setBackground(palette.panel());
        folderField.setForeground(palette.text());
        intervalSpinner.setBackground(palette.panel());
        intervalSpinner.setForeground(palette.text());

        styleButton(startBtn, palette);
        styleButton(stopBtn, palette);
        styleButton(groupByDay, palette);
        styleButton(groupByRange, palette);

        processLogArea.setBackground(palette.panel());
        processLogArea.setForeground(palette.text());
        if (processLogArea.getParent() instanceof JViewport viewport &&
                viewport.getParent() instanceof JComponent scroll) {
            viewport.setBackground(palette.panel());
            scroll.setBackground(palette.panel());
        }

        for (JSpinner spinner : new JSpinner[]{fromDateSpinner, toDateSpinner, daySpinner}) {
            spinner.setBackground(palette.panel());
            spinner.setForeground(palette.text());
        }

        historyTable.setBackground(palette.panel());
        historyTable.setForeground(palette.text());
        if (historyTable.getTableHeader() != null) {
            historyTable.getTableHeader().setBackground(palette.background());
            historyTable.getTableHeader().setForeground(palette.text());
        }
        if (historyTable.getParent() instanceof JViewport viewport) {
            viewport.setBackground(palette.panel());
            if (viewport.getParent() instanceof JComponent scroll) {
                scroll.setBackground(palette.panel());
            }
        }
        repaint();
    }

    private void styleButton(AbstractButton button, ThemePalette palette) {
        button.setBackground(palette.accent());
        button.setForeground(palette.accentText());
        button.setOpaque(true);
        button.setBorder(
                BorderFactory.createLineBorder(palette.accent().darker()));
    }

    public void onShow() {
        refreshHistory();
    }

    public void stopService() {
        if (service != null && service.isRunning()) {
            service.stop();
        }
        SwingUtilities.invokeLater(() -> {
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
        });
    }

    private void onStart() {
        String folder = folderField.getText().trim();
        if (folder.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择扫描目录");
            return;
        }
        AppSettings appSettings = AppSettings.get();
        SmartInspectionConfig config = new SmartInspectionConfig(
                folder,
                ((Number) intervalSpinner.getValue()).longValue(),
                appSettings.apiToken(),
                appSettings.uploadUrl(),
                appSettings.completionUrl()
        );
        persistPrefs(config);
        log("==============================");
        log("准备启动: " + config.folder());
        service = new SmartInspectionService(this);
        service.start(config);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
    }

    private void onStop() {
        if (service != null) {
            service.stop();
        }
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        log("智能点检已停止");
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(folderField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // =============== 日志输出（带样式） =================

    @Override
    public void log(String message) {
        SwingUtilities.invokeLater(() -> appendStyledLogLine(message));
    }

    private void appendStyledLogLine(String message) {
        StyledDocument doc = processLogArea.getStyledDocument();
        int baseSize = processLogArea.getFont().getSize();

        String ts = logTsFormat.format(new Date());

        // 时间戳样式
        SimpleAttributeSet tsAttrs = new SimpleAttributeSet();
        StyleConstants.setForeground(tsAttrs, LOG_TS_COLOR);
        StyleConstants.setFontSize(tsAttrs, baseSize);

        // 文本样式：根据内容分类着色
        SimpleAttributeSet msgAttrs = styleForMessage(message, baseSize);

        try {
            doc.insertString(doc.getLength(), ts + " - ", tsAttrs);
            doc.insertString(doc.getLength(), message + "\n", msgAttrs);
            processLogArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private SimpleAttributeSet styleForMessage(String msg, int baseSize) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontSize(attrs, baseSize);

        String m = msg == null ? "" : msg;

        // 分隔行或明显阶段标记
        if (m.startsWith("=") || m.contains("准备启动") || m.contains("停止")) {
            StyleConstants.setForeground(attrs, LOG_HEADER_COLOR);
            StyleConstants.setBold(attrs, true);
            return attrs;
        }

        // 成功类
        if (m.contains("识别完成") ||
                m.contains("结果已") ||
                m.contains("处理已结束") ||
                m.contains("点检结果") ||
                m.contains("已提交")) {
            StyleConstants.setForeground(attrs, LOG_SUCCESS_COLOR);
            StyleConstants.setBold(attrs, true);
            return attrs;
        }

        // 错误 / 异常 / 失败 / 超时
        if (m.contains("错误") ||
                m.contains("异常") ||
                m.contains("失败") ||
                m.contains("超时")) {
            StyleConstants.setForeground(attrs, LOG_ERROR_COLOR);
            StyleConstants.setBold(attrs, true);
            return attrs;
        }

        // 过程信息：扫描、发现图片、上传、平台处理中……
        if (m.contains("扫描目录") ||
                m.contains("当前智能点检") ||
                m.contains("发现图片") ||
                m.contains("发现新图片") ||
                m.contains("正在上传") ||
                m.contains("正在进行人脸识别") ||
                m.contains("正在进行人脸识别处理") ||
                m.contains("正在进行智能点检")) {
            StyleConstants.setForeground(attrs, LOG_NORMAL_COLOR);
            return attrs;
        }

        // 其余普通信息
        StyleConstants.setForeground(attrs, LOG_NORMAL_COLOR);
        return attrs;
    }

    @Override
    public void historyChanged(java.nio.file.Path historyDir) {
        SwingUtilities.invokeLater(this::refreshHistory);
    }

    // =============== 以下保持你原来的历史记录逻辑 ==============

    private void loadPrefs() {
        folderField.setText(
                prefs.get(KEY_FOLDER, System.getProperty("user.home", "")));
        intervalSpinner.setValue(prefs.getLong(KEY_INTERVAL, 60));
        setDateToStartOfDay(fromDateSpinner, new Date());
        setDateToEndOfDay(toDateSpinner, new Date());
        setDateToStartOfDay(daySpinner, new Date());
        toggleFilterMode();
        refreshHistory();
    }

    private void persistPrefs(SmartInspectionConfig cfg) {
        prefs.put(KEY_FOLDER, cfg.folder());
        prefs.putLong(KEY_INTERVAL, cfg.intervalSeconds());
    }

    private JSpinner createDateSpinner() {
        SpinnerDateModel model = new SpinnerDateModel(
                new Date(), null, null, java.util.Calendar.MINUTE);
        JSpinner spinner = new JSpinner(model);
        JSpinner.DateEditor editor =
                new JSpinner.DateEditor(spinner, "yyyy-MM-dd HH:mm:ss");
        spinner.setEditor(editor);
        return spinner;
    }

    public void refreshHistory() {
        historyTableModel.clear();
        String folder = folderField.getText().trim();
        if (folder.isEmpty()) {
            return;
        }
        Path history = Path.of(folder).resolve("his");
        DateRange range = currentRange();
        try {
            if (!Files.exists(history)) {
                return;
            }
            Files.list(history)
                    .filter(Files::isRegularFile)
                    .forEach(p -> addIfInRange(p, range));
        } catch (IOException e) {
            log("读取历史失败：" + e.getMessage());
        }
    }

    private void setDateToStartOfDay(JSpinner spinner, Date date) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        spinner.setValue(cal.getTime());
    }

    private void setDateToEndOfDay(JSpinner spinner, Date date) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        cal.set(java.util.Calendar.MILLISECOND, 999);
        spinner.setValue(cal.getTime());
    }

    private void toggleFilterMode() {
        boolean dayMode = groupByDay.isSelected();
        daySpinner.setEnabled(dayMode);
        fromDateSpinner.setEnabled(!dayMode);
        toDateSpinner.setEnabled(!dayMode);
    }

    private DateRange currentRange() {
        if (groupByDay.isSelected()) {
            Date d = (Date) daySpinner.getValue();
            Date start = toStartOfDay(d);
            Date end = toEndOfDay(d);
            return new DateRange(start, end);
        }
        return new DateRange(
                (Date) fromDateSpinner.getValue(),
                (Date) toDateSpinner.getValue());
    }

    private Date toStartOfDay(Date date) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private Date toEndOfDay(Date date) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        cal.set(java.util.Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    private void addIfInRange(Path p, DateRange range) {
        try {
            long mod = Files.getLastModifiedTime(p).toMillis();
            if (mod >= range.start().getTime() &&
                    mod <= range.end().getTime()) {
                historyTableModel.add(new HistoryRow(p));
            }
        } catch (IOException e) {
            // 忽略单个文件错误，继续加载
        }
    }

    private void openSelectedImage() {
        int viewRow = historyTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = historyTable.convertRowIndexToModel(viewRow);
        HistoryRow row = historyTableModel.get(modelRow);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(row.path().toFile());
            } else {
                JOptionPane.showMessageDialog(this, "当前环境不支持直接打开文件。");
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "打开图片失败：" + ex.getMessage());
        }
    }

    private record DateRange(Date start, Date end) {}

    private static class HistoryRow {
        private final Path path;
        private final Date time;
        private ImageIcon thumbnail;
        private boolean thumbnailLoaded;

        HistoryRow(Path path) throws IOException {
            this.path = path;
            this.time = new Date(Files.getLastModifiedTime(path).toMillis());
        }

        Path path() { return path; }

        Date time() { return time; }

        ImageIcon thumbnail() {
            if (!thumbnailLoaded) {
                thumbnail = createThumb(path);
                thumbnailLoaded = true;
            }
            return thumbnail;
        }

        String fileName() { return path.getFileName().toString(); }

        private ImageIcon createThumb(Path p) {
            try {
                java.awt.image.BufferedImage img =
                        javax.imageio.ImageIO.read(p.toFile());
                if (img == null) {
                    return new ImageIcon();
                }
                int targetW = 120;
                int targetH = 80;
                Image scaled = img.getScaledInstance(
                        targetW, targetH, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            } catch (IOException e) {
                return new ImageIcon();
            }
        }
    }

    private static class HistoryTableModel extends AbstractTableModel {
        private final java.util.List<HistoryRow> rows =
                new java.util.ArrayList<>();
        private final String[] cols = {"文件名", "扫描时间", "缩略图"};
        private final SimpleDateFormat fmt =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public int getRowCount() { return rows.size(); }

        @Override
        public int getColumnCount() { return cols.length; }

        @Override
        public String getColumnName(int column) { return cols[column]; }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 2) return ImageIcon.class;
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HistoryRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.fileName();
                case 1 -> fmt.format(row.time());
                case 2 -> row.thumbnail();
                default -> "";
            };
        }

        void add(HistoryRow row) {
            rows.add(row);
            fireTableDataChanged();
        }

        void clear() {
            rows.clear();
            fireTableDataChanged();
        }

        HistoryRow get(int index) { return rows.get(index); }
    }
}
