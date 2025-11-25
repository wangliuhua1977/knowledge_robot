package com.knowledge.robot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.knowledge.robot.http.ChatClient;
import com.knowledge.robot.util.AppConfig;
import com.knowledge.robot.util.IdUtil;
import com.knowledge.robot.util.QuestionBank;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * 仅流式自动聊天服务：
 * - 每轮 1 个 chatId（随机 12 位）；
 * - 对话框：展示“用户/助手”+ 末尾 '-------' 分隔；
 * - 思考栏：即时输出 <think>…</think> 中的内容（中文段落首行缩进、不中断、不换行）；
 * - 每轮结束：发送标记 '<<CLEAR_THOUGHTS>>'，UI 收到后清空思考栏。
 */
public class AutoChatService {

    /** 单线程调度器：用于自动循环与手动单次统一承载 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auto-chat-scheduler");
        t.setDaemon(true);
        return t;
    });

    // 选择的类别（可动态变更）
    private final Supplier<List<String>> chosenCategories;

    // 间隔控制（仅自动模式用）
    private final Supplier<Boolean> randomInterval;
    private final Supplier<Integer> maxIntervalSeconds;

    // 对话输出（对话过程文本框）
    private final Consumer<String> convoOut;

    // 思考输出（思考栏）
    private final Consumer<String> thinkOut;

    // 一轮结束回调
    private final Runnable onFinishOneDialog;

    // 倒计时回调（仅自动模式）
    private final IntConsumer onCountdown;

    private final Random rnd = new Random();
    private ScheduledFuture<?> countdownTask;
    private volatile int remainingSeconds = 0;

    private final ObjectMapper om = new ObjectMapper();

    public AutoChatService(
            Supplier<List<String>> chosenCategories,
            Supplier<Boolean> randomInterval,
            Supplier<Integer> maxIntervalSeconds,
            Consumer<String> convoOut,
            Consumer<String> thinkOut,
            Runnable onFinishOneDialog,
            IntConsumer onCountdown
    ) {
        this.chosenCategories = chosenCategories;
        this.randomInterval = randomInterval;
        this.maxIntervalSeconds = maxIntervalSeconds;
        this.convoOut = convoOut;
        this.thinkOut = thinkOut;
        this.onFinishOneDialog = onFinishOneDialog;
        this.onCountdown = onCountdown;
    }

    /** 兼容：直接传 List */
    public AutoChatService(
            List<String> chosenCategories,
            Supplier<Boolean> randomInterval,
            Supplier<Integer> maxIntervalSeconds,
            Consumer<String> convoOut,
            Consumer<String> thinkOut,
            Runnable onFinishOneDialog,
            IntConsumer onCountdown
    ) {
        this(() -> chosenCategories, randomInterval, maxIntervalSeconds,
                convoOut, thinkOut, onFinishOneDialog, onCountdown);
    }

    /* ========================= 对外方法 ========================= */

    /** 启动自动循环（会定时抽题） */
    public void start(AtomicBoolean runningFlag) {
        scheduler.execute(() -> doChat(null, true, runningFlag));
    }

    /** 停止自动循环（不关闭线程池，便于手工单次继续使用） */
    public void stop() {
        if (countdownTask != null) countdownTask.cancel(true);
        // 不要 scheduler.shutdownNow()，以便 onSendCustom() 时还能 askOnce()
    }

    /** 手工单次问答：未启动自动也可调用 */
    public void askOnce(String question) {
        final String q = (question == null || question.isBlank())
                ? "请给出与中国电信内部财务或流程有关的规范指引与办理要点。"
                : question.trim();
        scheduler.execute(() -> doChat(q, false, null));
    }

    /* ========================= 内部流程 ========================= */

    /** 执行一轮聊天；providedQuestion==null 则自动抽题；scheduleNext 控制是否在结束时继续调度下一轮 */
    private void doChat(String providedQuestion, boolean scheduleNext, AtomicBoolean runningFlag) {
        if (scheduleNext && (runningFlag != null && !runningFlag.get())) return;

        try {
            // 1) 题目
            String category = pickOneCategory();
            String question = (providedQuestion != null) ? providedQuestion : QuestionBank.randomQuestion(category);
            if (question == null || question.isBlank()) {
                question = "请给出与中国电信内部财务或流程有关的规范指引与办理要点。";
            }
            final String chatId = IdUtil.randomId(12);

            // 对话框：显示用户问题
            convoOut.accept("我: " + question);

            // 2) 请求体（强制流式）
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            root.put("chatId", chatId);
            root.put("stream", true);
            ArrayNode msgs = root.putArray("messages");
            ObjectNode msg = msgs.addObject();
            msg.put("role", "user");
            msg.put("content", question);

            ArrayNode refsArr = root.putArray("refs");
            for (Integer i : AppConfig.refs()) refsArr.add(i);

            root.put("agentlink", AppConfig.agentLink());

            String reqJson = om.writeValueAsString(root);

            // 3) 发起流式
            ChatClient cli = new ChatClient(AppConfig.url(), AppConfig.token());

            final StringBuilder answer = new StringBuilder();

            // 解析 <think>…</think>，把 think 内容推给思考栏（不换行、无时间）
            final boolean[] inThink = { false };

            cli.postJsonStream(reqJson, line -> {
                String payload = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                if (payload.isEmpty() || (!payload.startsWith("{") && !payload.startsWith("["))) return;

                String piece = extractDeltaContentFromJsonLine(payload);
                if (piece == null || piece.isEmpty()) return;

                String remain = piece;
                while (!remain.isEmpty()) {
                    if (!inThink[0]) {
                        int open = remain.indexOf("<think>");
                        if (open >= 0) {
                            if (open > 0) answer.append(remain, 0, open);
                            remain = remain.substring(open + 7);
                            inThink[0] = true;
                            // 中文段落首行缩进（两个全角空格）
                            thinkOut.accept("\u3000\u3000");
                        } else {
                            answer.append(remain);
                            break;
                        }
                    } else {
                        int close = remain.indexOf("</think>");
                        String out;
                        if (close >= 0) {
                            out = remain.substring(0, close);
                            remain = remain.substring(close + 8);
                            inThink[0] = false;
                        } else {
                            out = remain;
                            remain = "";
                        }
                        if (!out.isEmpty()) {
                            thinkOut.accept(out); // 打字机式追加
                        }
                    }
                }
            });

            // 4) 输出最终答案
            String assistant = cleanFinalAnswer(answer.toString());
            if (assistant == null || assistant.isBlank()) assistant = "(返回为空或无法解析)";
            convoOut.accept("助手: " + assistant);
            convoOut.accept("-------");

        } catch (Exception ex) {
            // 思考栏展示异常信息（无时间戳）
            thinkOut.accept("[ERROR] " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            // 通知 UI 清空思考栏
            thinkOut.accept("<<CLEAR_THOUGHTS>>");
            if (onFinishOneDialog != null) onFinishOneDialog.run();

            // 自动模式：安排下一轮
            if (scheduleNext) {
                scheduleNext(runningFlag);
            }
        }
    }

    /** 调度下一轮，并做秒级倒计时回调（仅自动模式） */
    private void scheduleNext(AtomicBoolean runningFlag) {
        if (runningFlag != null && !runningFlag.get()) return;

        int max = Math.max(1, maxIntervalSeconds.get());
        int next = randomInterval.get() ? (1 + rnd.nextInt(max)) : max;

        remainingSeconds = next;
        onCountdown.accept(remainingSeconds);

        if (countdownTask != null) countdownTask.cancel(true);
        countdownTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                remainingSeconds--;
                if (remainingSeconds < 0) remainingSeconds = 0;
                onCountdown.accept(remainingSeconds);
            } catch (Throwable ignore) {}
        }, 1, 1, TimeUnit.SECONDS);

        scheduler.schedule(() -> doChat(null, true, runningFlag), next, TimeUnit.SECONDS);
    }

    /** 从候选/题库随机取一个分类 */
    private String pickOneCategory() {
        List<String> chosen = chosenCategories.get();
        if (chosen != null && !chosen.isEmpty()) {
            return chosen.get(rnd.nextInt(chosen.size()));
        }
        Set<String> all = QuestionBank.categories();
        if (all == null || all.isEmpty()) return "财务流程";
        int idx = rnd.nextInt(all.size());
        int i = 0;
        for (String c : all) { if (i++ == idx) return c; }
        return "财务流程";
    }

    /** 解析单行 JSON（SSE 载荷）里的 delta/message.content 文本 */
    private String extractDeltaContentFromJsonLine(String jsonLine) {
        try {
            JsonNode root = om.readTree(jsonLine);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode first = choices.get(0);
                JsonNode delta = first.get("delta");
                if (delta != null && delta.isObject()) {
                    JsonNode content = delta.get("content");
                    if (content != null && !content.isNull()) return content.asText();
                }
                JsonNode message = first.get("message");
                if (message != null && message.isObject()) {
                    JsonNode content = message.get("content");
                    if (content != null && !content.isNull()) return content.asText();
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    /** 清洗最终答案：去 think、规范空白、句子级去重 */
    private String cleanFinalAnswer(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll("(?is)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("[ \\t]+", " ")
                .replaceAll("[\\r\\n]+", "\n")
                .trim();

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("[^。！？!?\\n]+[。！？!?\\n]*");
        java.util.regex.Matcher m = p.matcher(cleaned);
        while (m.find()) {
            String s = m.group().trim();
            if (!s.isEmpty()) ordered.add(s);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : ordered) {
            if (sb.length() > 0 && !sb.toString().endsWith("\n")) sb.append("\n");
            sb.append(s);
        }
        return sb.toString().trim();
    }
}
