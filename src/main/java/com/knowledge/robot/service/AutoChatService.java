package com.knowledge.robot.service;

import com.knowledge.robot.util.QuestionBank;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class AutoChatService {
    private final Supplier<List<String>> categorySupplier;
    private final Supplier<Boolean> randomIntervalSupplier;
    private final Supplier<Integer> maxSecondsSupplier;
    private final Consumer<String> convoConsumer;
    private final Consumer<String> thinkConsumer;
    private final Runnable onFinished;
    private final IntConsumer countdownUpdater;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auto-chat-loop");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean started = new AtomicBoolean(false);

    public AutoChatService(Supplier<List<String>> categorySupplier,
                           Supplier<Boolean> randomIntervalSupplier,
                           Supplier<Integer> maxSecondsSupplier,
                           Consumer<String> convoConsumer,
                           Consumer<String> thinkConsumer,
                           Runnable onFinished,
                           IntConsumer countdownUpdater) {
        this.categorySupplier = Objects.requireNonNull(categorySupplier);
        this.randomIntervalSupplier = Objects.requireNonNull(randomIntervalSupplier);
        this.maxSecondsSupplier = Objects.requireNonNull(maxSecondsSupplier);
        this.convoConsumer = Objects.requireNonNull(convoConsumer);
        this.thinkConsumer = Objects.requireNonNull(thinkConsumer);
        this.onFinished = Objects.requireNonNull(onFinished);
        this.countdownUpdater = Objects.requireNonNull(countdownUpdater);
    }

    public void start(AtomicBoolean runningFlag) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        scheduler.submit(() -> loop(runningFlag));
    }

    public void stop() {
        started.set(false);
        scheduler.shutdownNow();
    }

    public void askOnce(String question) {
        if (question == null || question.isBlank()) {
            return;
        }
        thinkConsumer.accept("<<CLEAR_THOUGHTS>>");
        thinkConsumer.accept(timestamp() + " 思考: 正在准备回答...\n");
        convoConsumer.accept("我: " + question);
        // 模拟助手回复
        convoConsumer.accept("助手: " + "自动学习占位实现，您的问题已记录。\n");
        onFinished.run();
    }

    private void loop(AtomicBoolean runningFlag) {
        while (runningFlag.get() && started.get()) {
            int waitSeconds = calculateIntervalSeconds();
            for (int i = waitSeconds; i >= 0 && runningFlag.get() && started.get(); i--) {
                countdownUpdater.accept(i);
                sleepOneSecond();
            }
            if (!runningFlag.get() || !started.get()) {
                break;
            }
            askOnce(QuestionBank.randomQuestion(categorySupplier.get()));
        }
    }

    private int calculateIntervalSeconds() {
        int max = Math.max(1, maxSecondsSupplier.get());
        if (Boolean.TRUE.equals(randomIntervalSupplier.get())) {
            return 1 + (int) (Math.random() * max);
        }
        return max;
    }

    private void sleepOneSecond() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String timestamp() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
    }
}
