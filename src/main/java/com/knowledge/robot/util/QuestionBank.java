package com.knowledge.robot.util;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class QuestionBank {

    private static final List<String> CATEGORIES = List.of(
            "公司战略", "安全生产", "网络维护", "客户服务", "市场销售", "团队建设",
            "技能提升", "应急处置", "项目管理", "数字化转型", "行业动态", "日常工作"
    );

    private static final List<String> QUESTIONS = Arrays.asList(
            "今天的学习重点是什么？",
            "最近有哪些新的政策需要关注？",
            "如何提升客户满意度？",
            "团队遇到问题时的最佳沟通方式是什么？",
            "近期有哪些行业热点？"
    );

    private QuestionBank() {
    }

    public static List<String> categories() {
        return CATEGORIES;
    }

    public static String randomQuestion(List<String> selectedCategories) {
        if (selectedCategories == null || selectedCategories.isEmpty()) {
            return QUESTIONS.get(ThreadLocalRandom.current().nextInt(QUESTIONS.size()));
        }
        int idx = ThreadLocalRandom.current().nextInt(QUESTIONS.size());
        return "[" + String.join(",", selectedCategories) + "] " + QUESTIONS.get(idx);
    }
}
