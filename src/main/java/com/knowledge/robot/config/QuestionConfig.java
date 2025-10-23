package com.knowledge.robot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionConfig {
    private final List<QuestionCategory> categories;

    @JsonCreator
    public QuestionConfig(@JsonProperty("categories") List<QuestionCategory> categories) {
        this.categories = categories == null ? new ArrayList<>() : new ArrayList<>(categories);
    }

    public List<QuestionCategory> getCategories() {
        return Collections.unmodifiableList(categories);
    }
}
