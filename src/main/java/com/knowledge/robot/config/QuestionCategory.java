package com.knowledge.robot.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionCategory {
    private final String name;
    private final List<String> questions;

    @JsonCreator
    public QuestionCategory(@JsonProperty("name") String name,
                            @JsonProperty("questions") List<String> questions) {
        this.name = name;
        this.questions = questions == null ? new ArrayList<>() : new ArrayList<>(questions);
    }

    public String getName() {
        return name;
    }

    public List<String> getQuestions() {
        return Collections.unmodifiableList(questions);
    }
}
