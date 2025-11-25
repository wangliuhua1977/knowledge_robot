package com.knowledge.robot.ui;

import com.knowledge.robot.inspection.SmartInspectionConfig;
import com.knowledge.robot.inspection.SmartInspectionLogger;
import com.knowledge.robot.inspection.SmartInspectionService;
import com.knowledge.robot.util.AppSettings;

import javax.swing.*;
import javax.swing.border.TitledBorder;
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
    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(60, 5, 3600, 5));
    private final JButton startBtn = new JButton("启动任务");
    private final JButton stopBtn = new JButton("停止");
    private final JTextArea logArea = new JTextArea();
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyModel);
    private final JSpinner fromDateSpinner;
    private final JSpinner toDateSpinner;

    private SmartInspectionService service;

    public SmartInspectionPanel() {
        this.fromDateSpinner = createDateSpinner();
        this.toDateSpinner = createDateSpinner();
        setLayout(new BorderLayout(8, 8));
        buildUI();
        bindActions();
        loadPrefs();
    }

    private void buildUI() {
        JPanel params = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.gridy = 0; params.add(new JLabel("扫描目录"), gc);
        gc.gridx = 1; gc.weightx = 1; params.add(folderField, gc);
        JButton browse = new JButton("选择");
        gc.gridx = 2; gc.weightx = 0; params.add(browse, gc);
        browse.addActionListener(e -> chooseFolder());

        gc.gridx = 0; gc.gridy = 1; params.add(new JLabel("间隔(秒)"), gc);
        gc.gridx = 1; params.add(intervalSpinner, gc);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topButtons.add(startBtn);
        topButtons.add(stopBtn);
        stopBtn.setEnabled(false);

        JPanel paramBorder = new JPanel(new BorderLayout());
        paramBorder.setBorder(new TitledBorder("任务参数设置"));
        paramBorder.add(params, BorderLayout.CENTER);
        paramBorder.add(topButtons, BorderLayout.SOUTH);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("处理日志"));

        JPanel historyPanel = new JPanel(new BorderLayout());
        JPanel historyFilter = new JPanel(new GridBagLayout());
        GridBagConstraints hg = new GridBagConstraints();
        hg.insets = new Insets(4, 4, 4, 4);
        hg.fill = GridBagConstraints.HORIZONTAL;
        hg.gridx = 0; hg.gridy = 0; historyFilter.add(new JLabel("开始时间"), hg);
        hg.gridx = 1; historyFilter.add(fromDateSpinner, hg);
        hg.gridx = 0; hg.gridy = 1; historyFilter.add(new JLabel("结束时间"), hg);
        hg.gridx = 1; historyFilter.add(toDateSpinner, hg);
        JButton refreshHistory = new JButton("刷新历史");
        hg.gridx = 2; hg.gridy = 0; hg.gridheight = 2; hg.fill = GridBagConstraints.VERTICAL;
        historyFilter.add(refreshHistory, hg);
        refreshHistory.addActionListener(e -> refreshHistory());

        historyList.setVisibleRowCount(10);
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setBorder(new TitledBorder("历史处理记录"));
        historyPanel.add(historyFilter, BorderLayout.NORTH);
        historyPanel.add(historyScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, logScroll, historyPanel);
        split.setDividerLocation(320);

        add(paramBorder, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private void bindActions() {
        startBtn.addActionListener(e -> onStart());
        stopBtn.addActionListener(e -> onStop());
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
        log("准备启动：" + config.folder());
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
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(folderField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    @Override
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            logArea.append(ts + " - " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void loadPrefs() {
        folderField.setText(prefs.get(KEY_FOLDER, System.getProperty("user.home", "")));
        intervalSpinner.setValue(prefs.getLong(KEY_INTERVAL, 60));
        setDateToStartOfDay(fromDateSpinner, new Date());
        setDateToEndOfDay(toDateSpinner, new Date());
        refreshHistory();
    }

    private void persistPrefs(SmartInspectionConfig cfg) {
        prefs.put(KEY_FOLDER, cfg.folder());
        prefs.putLong(KEY_INTERVAL, cfg.intervalSeconds());
    }

    private JSpinner createDateSpinner() {
        SpinnerDateModel model = new SpinnerDateModel(new Date(), null, null, java.util.Calendar.MINUTE);
        JSpinner spinner = new JSpinner(model);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd HH:mm:ss");
        spinner.setEditor(editor);
        return spinner;
    }

    public void refreshHistory() {
        historyModel.clear();
        String folder = folderField.getText().trim();
        if (folder.isEmpty()) {
            return;
        }
        Path history = Path.of(folder).resolve("his");
        Date from = (Date) fromDateSpinner.getValue();
        Date to = (Date) toDateSpinner.getValue();
        try {
            if (!Files.exists(history)) {
                return;
            }
            Files.list(history)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            long mod = Files.getLastModifiedTime(p).toMillis();
                            return mod >= from.getTime() && mod <= to.getTime();
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .sorted()
                    .forEach(p -> historyModel.addElement(p.getFileName().toString()));
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
}
