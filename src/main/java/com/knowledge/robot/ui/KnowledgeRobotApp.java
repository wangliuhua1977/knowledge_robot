package com.knowledge.robot.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.knowledge.robot.config.QuestionCategory;
import com.knowledge.robot.config.QuestionConfig;
import com.knowledge.robot.http.ChatClient;
import com.knowledge.robot.model.ChatMessage;
import com.knowledge.robot.model.ChatRequest;
import com.knowledge.robot.model.ChatResponse;
import com.knowledge.robot.service.ConversationState;
import com.knowledge.robot.service.QuestionConfigLoader;
import com.knowledge.robot.service.QuestionGenerator;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.nio.file.Path;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertificateException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KnowledgeRobotApp extends JFrame {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final QuestionConfigLoader configLoader = new QuestionConfigLoader(objectMapper);
    private final QuestionGenerator questionGenerator = new QuestionGenerator();
    private ChatClient chatClient = new ChatClient(objectMapper);
    private final ConversationState conversationState = new ConversationState();
    private final Map<QuestionCategory, JCheckBox> categoryCheckBoxes = new LinkedHashMap<>();
    private final Random random = new Random();

    private JTextField endpointField;
    private JTextField tokenField;
    private JSpinner intervalSpinner;
    private JSpinner minRoundsSpinner;
    private JSpinner maxRoundsSpinner;
    private JCheckBox streamCheckBox;
    private JCheckBox trustAllCheckBox;
    private JTextField refsField;
    private JTextArea agentLinkArea;
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private JButton reloadButton;
    private JButton inspectionButton;
    private JPanel categoryPanel;

    private ScheduledExecutorService executorService;
    private QuestionConfig questionConfig;
    private IntelligentInspectionDialog inspectionDialog;

    public KnowledgeRobotApp() {
        setTitle("中国电信内部知识问答机器人");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 760);
        setLocationRelativeTo(null);
        initComponents();
        reloadConfiguration();
    }

    private void initComponents() {
        setLayout(new BorderLayout(8, 8));

        JPanel configPanel = buildConfigPanel();
        add(configPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.add(buildCategoryPanel(), BorderLayout.WEST);
        centerPanel.add(buildLogPanel(), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("接口与调度配置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        endpointField = new JTextField("https://openai.sc.ctc.com:8898/whaleagent/knowledgeService/api/v1/chat/completions");
        tokenField = new JTextField("WhaleDI-Agent-6ade2321ada01f69fa7a465135ce65a02262408d006e25236788c7c08b86be20");
        intervalSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 3600, 1));
        minRoundsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
        maxRoundsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        streamCheckBox = new JCheckBox("启用流式返回", true);
        trustAllCheckBox = new JCheckBox("忽略SSL证书校验(内部测试)", true);
        refsField = new JTextField("23,24,35");
        agentLinkArea = new JTextArea(3, 20);
        agentLinkArea.setText("{\"key1\":\"value1\",\"key2\":\"value2\"}");

        startButton = new JButton("开始");
        stopButton = new JButton("停止");
        reloadButton = new JButton("重新加载问题配置");
        inspectionButton = new JButton("智能点检");
        stopButton.setEnabled(false);

        startButton.addActionListener(e -> startAutomation());
        stopButton.addActionListener(e -> stopAutomation());
        reloadButton.addActionListener(e -> reloadConfiguration());
        inspectionButton.addActionListener(e -> openInspectionDialog());

        gbc.weightx = 0;
        panel.add(new JLabel("接口地址"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(endpointField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("授权令牌"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(tokenField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("调用间隔(秒)"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(intervalSpinner, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        panel.add(new JLabel("单个对话轮次范围"), gbc);
        JPanel roundsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        roundsPanel.add(new JLabel("最少:"));
        roundsPanel.add(minRoundsSpinner);
        roundsPanel.add(new JLabel("最多:"));
        roundsPanel.add(maxRoundsSpinner);
        gbc.gridx = 1;
        panel.add(roundsPanel, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        panel.add(new JLabel("引用文档refs"), gbc);
        gbc.gridx = 1;
        panel.add(refsField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        panel.add(new JLabel("agentlink JSON"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(agentLinkArea), gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridy++;
        gbc.gridx = 0;
        panel.add(streamCheckBox, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        panel.add(trustAllCheckBox, gbc);

        gbc.gridx = 1;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.add(reloadButton);
        buttonPanel.add(inspectionButton);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private JScrollPane buildLogPanel() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(new TitledBorder("调用日志"));
        return scrollPane;
    }

    private JPanel buildCategoryPanel() {
        categoryPanel = new JPanel();
        categoryPanel.setBorder(new TitledBorder("问题大类"));
        categoryPanel.setLayout(new BoxLayout(categoryPanel, BoxLayout.Y_AXIS));
        return categoryPanel;
    }

    private void reloadConfiguration() {
        Path configPath = Path.of("config", "questions.json");
        Optional<QuestionConfig> configOptional = configLoader.load(configPath);
        configOptional.ifPresent(config -> this.questionConfig = config);
        if (questionConfig == null) {
            questionConfig = new QuestionConfig(new ArrayList<>());
        }
        rebuildCategoryPanel();
        appendLog("问题配置已加载，共" + questionConfig.getCategories().size() + "个大类。");
    }

    private void rebuildCategoryPanel() {
        categoryPanel.removeAll();
        categoryCheckBoxes.clear();
        for (QuestionCategory category : questionConfig.getCategories()) {
            JCheckBox checkBox = new JCheckBox(category.getName(), true);
            categoryCheckBoxes.put(category, checkBox);
            categoryPanel.add(checkBox);
        }
        categoryPanel.revalidate();
        categoryPanel.repaint();
    }

    private void startAutomation() {
        if (executorService != null && !executorService.isShutdown()) {
            appendLog("调度已经在运行。");
            return;
        }
        int minRounds = ((Number) minRoundsSpinner.getValue()).intValue();
        int maxRounds = ((Number) maxRoundsSpinner.getValue()).intValue();
        if (minRounds > maxRounds) {
            appendLog("最小轮次不能大于最大轮次。");
            return;
        }
        String endpoint = endpointField.getText().trim();
        String token = tokenField.getText().trim();
        if (endpoint.isEmpty() || token.isEmpty()) {
            appendLog("接口地址和授权令牌不能为空。");
            return;
        }
        List<Long> refs = parseRefs(refsField.getText());
        Map<String, String> agentLink = ChatClient.parseAgentLink(objectMapper, agentLinkArea.getText());
        boolean stream = streamCheckBox.isSelected();
        long intervalSeconds = ((Number) intervalSpinner.getValue()).longValue();

        if (getActiveCategories().isEmpty()) {
            appendLog("请至少选择一个问题大类。");
            return;
        }

        chatClient = new ChatClient(objectMapper, trustAllCheckBox.isSelected());
        executorService = Executors.newSingleThreadScheduledExecutor();
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        appendLog("开始自动轮询调用，间隔" + intervalSeconds + "秒，SSL证书校验=" +
                (trustAllCheckBox.isSelected() ? "已忽略" : "已启用") + "。");
        resetConversation(minRounds, maxRounds);

        executorService.scheduleAtFixedRate(() -> runChatCycle(endpoint, token, stream, refs, agentLink, minRounds, maxRounds),
                0, intervalSeconds, TimeUnit.SECONDS);
    }

    private void stopAutomation() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
            appendLog("调度已停止。");
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void resetConversation(int minRounds, int maxRounds) {
        int rounds = randomRounds(minRounds, maxRounds);
        conversationState.reset(rounds);
        appendLog("开始新的对话，chatId=" + conversationState.getChatId() + "，目标轮次=" + rounds + "。");
    }

    private void runChatCycle(String endpoint,
                               String token,
                               boolean stream,
                               List<Long> refs,
                               Map<String, String> agentLink,
                               int minRounds,
                               int maxRounds) {
        try {
            if (conversationState.shouldReset() || conversationState.getChatId() == null) {
                resetConversation(minRounds, maxRounds);
            }
            List<QuestionCategory> activeCategories = getActiveCategories();
            if (activeCategories.isEmpty()) {
                appendLog("无可用问题大类，暂停提问。");
                stopAutomation();
                return;
            }
            QuestionCategory category = activeCategories.get(random.nextInt(activeCategories.size()));
            String question = questionGenerator.generateQuestion(category);
            ChatMessage userMessage = new ChatMessage("user", question);

            List<ChatMessage> messages = new ArrayList<>(conversationState.getMessages());
            messages.add(userMessage);
            ChatRequest request = new ChatRequest(conversationState.getChatId(), stream, messages, refs, agentLink);
            String requestBody = objectMapper.writeValueAsString(request);
            appendLog("-> 提问类别: " + category.getName());
            appendLog("-> 请求报文:\n" + requestBody);

            ChatResponse response = chatClient.sendChat(endpoint, token, request, line -> appendLog("<- " + line));
            String assistantContent = response.getAssistantMessage();
            if (assistantContent == null || assistantContent.isBlank()) {
                assistantContent = response.getRawBody();
            }
            appendLog("<- 汇总回答:\n" + assistantContent);

            conversationState.addMessage(userMessage);
            conversationState.addMessage(new ChatMessage("assistant", assistantContent));
            conversationState.incrementRound();
            appendLog("对话已完成第" + conversationState.getCompletedRounds() + "轮/目标" + conversationState.getTargetRounds() + "轮。");
        } catch (Exception ex) {
            appendLog("调用出现异常: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            if (isCertificateError(ex)) {
                if (trustAllCheckBox.isSelected()) {
                    appendLog("提示: 已启用忽略SSL证书但仍然失败，请确认接口地址或联系证书管理员。");
                } else {
                    appendLog("提示: 检测到SSL证书问题，可勾选\"忽略SSL证书校验(内部测试)\"后重新开始。");
                }
            }
        }
    }

    private boolean isCertificateError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SSLHandshakeException || current instanceof CertPathBuilderException || current instanceof CertificateException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("PKIX path building failed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private List<QuestionCategory> getActiveCategories() {
        List<QuestionCategory> active = new ArrayList<>();
        for (Map.Entry<QuestionCategory, JCheckBox> entry : categoryCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                active.add(entry.getKey());
            }
        }
        return active;
    }

    private List<Long> parseRefs(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        Arrays.stream(text.split("[,，\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(s -> {
                    try {
                        result.add(Long.parseLong(s));
                    } catch (NumberFormatException ignored) {
                    }
                });
        return result;
    }

    private int randomRounds(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().format(TIME_FORMATTER);
            logArea.append("[" + time + "] " + message + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void openInspectionDialog() {
        if (inspectionDialog == null) {
            inspectionDialog = new IntelligentInspectionDialog(this, objectMapper, trustAllCheckBox.isSelected(), tokenField.getText(), endpointField.getText());
        }
        inspectionDialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            KnowledgeRobotApp app = new KnowledgeRobotApp();
            app.setVisible(true);
        });
    }
}
