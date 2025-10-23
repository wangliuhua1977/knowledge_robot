package com.knowledge.robot.service;

import com.knowledge.robot.config.QuestionCategory;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QuestionGenerator {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private final Random random = new SecureRandom();

    public String generateQuestion(QuestionCategory category) {
        if (category.getQuestions().isEmpty()) {
            return "请提供" + category.getName() + "的最新操作规范。";
        }
        List<String> questions = new ArrayList<>(category.getQuestions());
        String baseQuestion = questions.get(random.nextInt(questions.size()));
        String suffix = buildDynamicSuffix(category.getName());
        return suffix == null ? baseQuestion : baseQuestion + suffix;
    }

    private String buildDynamicSuffix(String categoryName) {
        int roll = random.nextInt(4);
        return switch (roll) {
            case 0 -> "（请结合" + LocalDate.now().minusDays(random.nextInt(10)).format(DATE_FORMATTER) + "发布的通知说明）";
            case 1 -> "。我们准备在" + LocalDate.now().plusDays(random.nextInt(15)).format(DATE_FORMATTER) + "前完成，请给出关键控制点。";
            case 2 -> "，并说明与上一版制度相比的变化。";
            default -> null;
        };
    }
}
