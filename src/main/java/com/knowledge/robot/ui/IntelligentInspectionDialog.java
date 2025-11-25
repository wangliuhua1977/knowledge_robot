package com.knowledge.robot.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.robot.http.ChatClient;
import com.knowledge.robot.model.ChatMessage;
import com.knowledge.robot.model.ChatRequest;
import com.knowledge.robot.model.ChatResponse;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * 智能点检任务窗口，用于扫描图片文件并串行调用上传与处理接口。
 */
public class IntelligentInspectionDialog extends JDialog {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PREF_FOLDER = "inspection.folder";

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final HttpClient uploadClient;
    private final Preferences preferences = Preferences.userNodeForPackage(IntelligentInspectionDialog.class);
    private final List<InspectionHistoryEntry> historyEntries = new ArrayList<>();
    private final InspectionHistoryTableModel historyTableModel = new InspectionHistoryTableModel();

    private JTextField folderField;
    private JTextField uploadEndpointField;
    private JTextField chatEndpointField;
    private JTextField tokenField;
    private JTextField chatIdField;
    private JTextField messageField;
    private JSpinner intervalSpinner;
    private JCheckBox streamCheckBox;
    private JTextArea logArea;
    private JTable historyTable;
    private JFormattedTextField startDateField;
    private JFormattedTextField endDateField;
    private JButton startButton;
    private JButton stopButton;

    private ScheduledExecutorService executorService;
    private volatile boolean running;

    public IntelligentInspectionDialog(Frame owner,
                                       ObjectMapper objectMapper,
                                       boolean trustAllCertificates,
                                       String defaultToken,
                                       String defaultChatEndpoint) {
        super(owner, "智能点检", true);
        this.objectMapper = objectMapper;
        this.chatClient = new ChatClient(objectMapper, trustAllCertificates);
        this.uploadClient = HttpClient.newBuilder().build();
        initComponents(defaultToken, defaultChatEndpoint);
        setSize(1100, 720);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                stopTask();
            }
        });
    }

    private void initComponents(String defaultToken, String defaultChatEndpoint) {
        setLayout(new BorderLayout(8, 8));

        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(new TitledBorder("任务参数"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        uploadEndpointField = new JTextField("https://openai.sc.ctc.com:8898/whaleagent/knowledgeService/core/chat/upload-files");
        chatEndpointField = new JTextField(defaultChatEndpoint);
        tokenField = new JTextField(defaultToken);
        chatIdField = new JTextField("13yg9vuuk6ny");
        messageField = new JTextField("智能点检");
        intervalSpinner = new JSpinner(new SpinnerNumberModel(60, 5, 3600, 5));
        streamCheckBox = new JCheckBox("启用流式返回", true);
        folderField = new JTextField(preferences.get(PREF_FOLDER, System.getProperty("user.home")));
        folderField.setEditable(false);

        JButton chooseFolderButton = new JButton("选择文件夹");
        chooseFolderButton.addActionListener(e -> chooseFolder());

        startButton = new JButton("启动任务");
        stopButton = new JButton("停止任务");
        stopButton.setEnabled(false);
        startButton.addActionListener(e -> startTask());
        stopButton.addActionListener(e -> stopTask());

        configPanel.add(new JLabel("上传接口"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        configPanel.add(uploadEndpointField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        configPanel.add(new JLabel("聊天接口"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        configPanel.add(chatEndpointField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        configPanel.add(new JLabel("授权令牌"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        configPanel.add(tokenField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        configPanel.add(new JLabel("ChatId"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        configPanel.add(chatIdField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        configPanel.add(new JLabel("指令内容"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        configPanel.add(messageField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        configPanel.add(new JLabel("扫描间隔(秒)"), gbc);
        gbc.gridx = 1;
        configPanel.add(intervalSpinner, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        configPanel.add(new JLabel("工作目录"), gbc);
        gbc.gridx = 1;
        JPanel folderPanel = new JPanel(new BorderLayout(4, 4));
        folderPanel.add(folderField, BorderLayout.CENTER);
        folderPanel.add(chooseFolderButton, BorderLayout.EAST);
        configPanel.add(folderPanel, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        configPanel.add(new JLabel("响应模式"), gbc);
        gbc.gridx = 1;
        configPanel.add(streamCheckBox, gbc);

        gbc.gridy++;
        gbc.gridx = 1;
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.add(startButton);
        btnPanel.add(stopButton);
        configPanel.add(btnPanel, gbc);

        add(configPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        centerPanel.add(buildLogPanel());
        centerPanel.add(buildHistoryPanel());
        add(centerPanel, BorderLayout.CENTER);
    }

    private JScrollPane buildLogPanel() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(new TitledBorder("处理日志"));
        return scrollPane;
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(new TitledBorder("处理历史"));

        historyTable = new JTable(historyTableModel);
        historyTable.setFillsViewportHeight(true);
        panel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        JPanel filterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        startDateField = new JFormattedTextField(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        endDateField = new JFormattedTextField(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        filterPanel.add(new JLabel("开始日期(yyyy-MM-dd)"), gbc);
        gbc.gridx = 1;
        filterPanel.add(startDateField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        filterPanel.add(new JLabel("结束日期(yyyy-MM-dd)"), gbc);
        gbc.gridx = 1;
        filterPanel.add(endDateField, gbc);

        gbc.gridy++;
        gbc.gridx = 1;
        JButton filterButton = new JButton("检索");
        filterButton.addActionListener(e -> applyHistoryFilter());
        filterPanel.add(filterButton, gbc);

        panel.add(filterPanel, BorderLayout.NORTH);
        return panel;
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(folderField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path selected = chooser.getSelectedFile().toPath();
            folderField.setText(selected.toString());
            preferences.put(PREF_FOLDER, selected.toString());
        }
    }

    private void startTask() {
        if (running) {
            appendLog("任务已在运行中。");
            return;
        }
        Path folder = Path.of(folderField.getText());
        if (!Files.isDirectory(folder)) {
            appendLog("请选择有效的文件夹。");
            return;
        }
        long interval = ((Number) intervalSpinner.getValue()).longValue();
        running = true;
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::runInspection,
                0, interval, TimeUnit.SECONDS);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        appendLog("任务已启动，扫描间隔" + interval + "秒。");
    }

    private void stopTask() {
        running = false;
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        appendLog("任务已停止。");
    }

    private void runInspection() {
        if (!running) {
            return;
        }
        Path folder = Path.of(folderField.getText());
        Path historyFolder = folder.resolve("his");
        try {
            Files.createDirectories(historyFolder);
        } catch (IOException e) {
            appendLog("无法创建历史目录: " + e.getMessage());
            return;
        }

        List<Path> images;
        try {
            images = Files.list(folder)
                    .filter(Files::isRegularFile)
                    .filter(this::isImage)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            appendLog("扫描文件夹失败: " + e.getMessage());
            return;
        }

        if (images.isEmpty()) {
            appendLog("当前没有待处理的图片。");
            return;
        }

        for (Path image : images) {
            if (!running) {
                break;
            }
            handleImage(image, historyFolder);
        }
    }

    private void handleImage(Path image, Path historyFolder) {
        String appId = generateAppId();
        appendLog("开始处理文件: " + image.getFileName() + "，appId=" + appId);
        try {
            Long refId = uploadImage(image, appId);
            if (refId == null) {
                appendLog("上传失败，跳过后续处理: " + image.getFileName());
                addHistory(image, "上传失败", null);
                return;
            }
            appendLog("上传成功，引用ID=" + refId + "，准备提交智能点检请求。");
            ChatResponse response = callCompletion(refId);
            appendLog("智能点检响应状态码: " + response.getStatusCode());
            appendLog("智能点检原始返回:\n" + response.getRawBody());
            moveToHistory(image, historyFolder);
            addHistory(image, "处理完成", response.getAssistantMessage());
        } catch (Exception e) {
            appendLog("处理文件时出现异常: " + e.getMessage());
            addHistory(image, "异常: " + e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private ChatResponse callCompletion(Long refId) throws IOException, InterruptedException {
        String chatId = chatIdField.getText().trim();
        boolean stream = streamCheckBox.isSelected();
        String message = messageField.getText().trim();
        String token = tokenField.getText().trim();
        String endpoint = chatEndpointField.getText().trim();

        ChatRequest request = new ChatRequest(chatId, stream, List.of(new ChatMessage("user", message)), List.of(refId), Map.of());
        String payload = objectMapper.writeValueAsString(request);
        appendLog("提交智能点检请求:\n" + payload);
        return chatClient.sendChat(endpoint, token, request, line -> appendLog("事件: " + line));
    }

    private Long uploadImage(Path image, String appId) throws IOException, InterruptedException {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadEndpointField.getText().trim()))
                .header("Authorization", "Bearer " + tokenField.getText().trim())
                .header("User-Agent", "KnowledgeRobot/inspection")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(buildMultipartBody(boundary, image, appId))
                .build();

        HttpResponse<String> response = uploadClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        appendLog("上传返回状态码: " + response.statusCode());
        appendLog("上传返回体:\n" + response.body());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            Long id = extractFirstId(root);
            if (id == null) {
                appendLog("未能从返回中解析到引用ID。");
            }
            return id;
        }
        return null;
    }

    private HttpRequest.BodyPublisher buildMultipartBody(String boundary, Path image, String appId) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String lineBreak = "\r\n";
        String fileName = image.getFileName().toString();

        output.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        String fileHeader = "Content-Disposition: form-data; name=\"files\"; filename=\"" + fileName + "\"" + lineBreak
                + "Content-Type: application/octet-stream" + lineBreak + lineBreak;
        output.write(fileHeader.getBytes(StandardCharsets.UTF_8));
        output.write(Files.readAllBytes(image));
        output.write(lineBreak.getBytes(StandardCharsets.UTF_8));

        output.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        String appIdPart = "Content-Disposition: form-data; name=\"appId\"" + lineBreak + lineBreak
                + appId + lineBreak;
        output.write(appIdPart.getBytes(StandardCharsets.UTF_8));

        output.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        String chatIdPart = "Content-Disposition: form-data; name=\"chatId\"" + lineBreak + lineBreak
                + chatIdField.getText().trim() + lineBreak;
        output.write(chatIdPart.getBytes(StandardCharsets.UTF_8));

        output.write(("--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArray(output.toByteArray());
    }

    private void moveToHistory(Path image, Path historyFolder) {
        try {
            Path target = historyFolder.resolve(image.getFileName());
            if (Files.exists(target)) {
                String fileName = image.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                String base = dot > 0 ? fileName.substring(0, dot) : fileName;
                String ext = dot > 0 ? fileName.substring(dot) : "";
                target = historyFolder.resolve(base + "_" + System.currentTimeMillis() + ext);
            }
            Files.move(image, target);
            appendLog("已移动文件到历史目录: " + target.getFileName());
        } catch (IOException e) {
            appendLog("移动文件失败: " + e.getMessage());
        }
    }

    private void addHistory(Path image, String status, String detail) {
        InspectionHistoryEntry entry = new InspectionHistoryEntry(LocalDateTime.now(), image.getFileName().toString(), status, detail);
        historyEntries.add(entry);
        historyTableModel.setEntries(historyEntries);
    }

    private void applyHistoryFilter() {
        LocalDate start = parseDate(startDateField.getText());
        LocalDate end = parseDate(endDateField.getText());
        historyTableModel.applyFilter(historyEntries, start, end);
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA));
        } catch (Exception e) {
            appendLog("日期格式错误: " + text);
            return null;
        }
    }

    private boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp") || name.endsWith(".gif");
    }

    private String generateAppId() {
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 19; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    private Long extractFirstId(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.has("id") && root.get("id").canConvertToLong()) {
            return root.get("id").asLong();
        }
        JsonNode data = root.get("data");
        if (data != null && data.isArray() && data.size() > 0) {
            JsonNode first = data.get(0);
            if (first.has("id") && first.get("id").canConvertToLong()) {
                return first.get("id").asLong();
            }
            if (first.canConvertToLong()) {
                return first.asLong();
            }
        }
        return null;
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(TIME_FORMATTER);
            logArea.append("[" + time + "] " + message + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static class InspectionHistoryEntry {
        private final LocalDateTime time;
        private final String fileName;
        private final String status;
        private final String detail;

        private InspectionHistoryEntry(LocalDateTime time, String fileName, String status, String detail) {
            this.time = time;
            this.fileName = fileName;
            this.status = status;
            this.detail = detail;
        }
    }

    private class InspectionHistoryTableModel extends AbstractTableModel {
        private final List<InspectionHistoryEntry> visibleEntries = new ArrayList<>();
        private final String[] columns = {"时间", "文件名", "状态", "摘要"};

        private void setEntries(List<InspectionHistoryEntry> entries) {
            applyFilter(entries, null, null);
        }

        private void applyFilter(List<InspectionHistoryEntry> source, LocalDate start, LocalDate end) {
            visibleEntries.clear();
            for (InspectionHistoryEntry entry : source) {
                LocalDate date = entry.time.toLocalDate();
                if (start != null && date.isBefore(start)) {
                    continue;
                }
                if (end != null && date.isAfter(end)) {
                    continue;
                }
                visibleEntries.add(entry);
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return visibleEntries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            InspectionHistoryEntry entry = visibleEntries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.time.format(TIME_FORMATTER);
                case 1 -> entry.fileName;
                case 2 -> entry.status;
                case 3 -> Optional.ofNullable(entry.detail).map(d -> d.length() > 120 ? d.substring(0, 117) + "..." : d).orElse("-");
                default -> "";
            };
        }
    }
}

