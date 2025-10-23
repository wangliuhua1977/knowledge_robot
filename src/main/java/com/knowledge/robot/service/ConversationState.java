package com.knowledge.robot.service;

import com.knowledge.robot.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConversationState {
    private final List<ChatMessage> messages = new ArrayList<>();
    private String chatId;
    private int completedRounds;
    private int targetRounds;

    public void reset(int targetRounds) {
        this.messages.clear();
        this.chatId = UUID.randomUUID().toString().replaceAll("-", "");
        this.completedRounds = 0;
        this.targetRounds = targetRounds;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public String getChatId() {
        return chatId;
    }

    public int getCompletedRounds() {
        return completedRounds;
    }

    public int getTargetRounds() {
        return targetRounds;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
    }

    public void incrementRound() {
        completedRounds++;
    }

    public boolean shouldReset() {
        return completedRounds >= targetRounds;
    }
}
