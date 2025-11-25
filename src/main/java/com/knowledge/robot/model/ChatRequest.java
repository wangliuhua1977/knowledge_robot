package com.knowledge.robot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {
    @JsonProperty("chatId")
    private final String chatId;

    @JsonProperty("stream")
    private final boolean stream;

    @JsonProperty("messages")
    private final List<ChatMessage> messages;

    @JsonProperty("refs")
    private final List<Long> refs;

    @JsonProperty("agentlink")
    private final Map<String, String> agentLink;

    public ChatRequest(String chatId,
                       boolean stream,
                       List<ChatMessage> messages,
                       List<Long> refs,
                       Map<String, String> agentLink) {
        this.chatId = chatId;
        this.stream = stream;
        this.messages = messages;
        this.refs = refs;
        this.agentLink = agentLink;
    }

    public String getChatId() {
        return chatId;
    }

    public boolean isStream() {
        return stream;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public List<Long> getRefs() {
        return refs;
    }

    public Map<String, String> getAgentLink() {
        return agentLink;
    }
}
