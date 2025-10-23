package com.knowledge.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.robot.config.QuestionConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class QuestionConfigLoader {
    private final ObjectMapper objectMapper;

    public QuestionConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<QuestionConfig> load(Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return Optional.of(objectMapper.readValue(bytes, QuestionConfig.class));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }
}
