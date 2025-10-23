package com.knowledge.robot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatResponse {
    private final int statusCode;
    private final String assistantMessage;
    private final List<String> events;
    private final String rawBody;

    private ChatResponse(Builder builder) {
        this.statusCode = builder.statusCode;
        this.assistantMessage = builder.assistantMessage;
        this.events = Collections.unmodifiableList(new ArrayList<>(builder.events));
        this.rawBody = builder.rawBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public List<String> getEvents() {
        return events;
    }

    public String getRawBody() {
        return rawBody;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int statusCode;
        private String assistantMessage;
        private List<String> events = new ArrayList<>();
        private String rawBody;

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder assistantMessage(String assistantMessage) {
            this.assistantMessage = assistantMessage;
            return this;
        }

        public Builder events(List<String> events) {
            this.events = events;
            return this;
        }

        public Builder rawBody(String rawBody) {
            this.rawBody = rawBody;
            return this;
        }

        public ChatResponse build() {
            return new ChatResponse(this);
        }
    }
}
