package com.study.llmgateway.chat.dto;

public record ChatResponse(
        String sessionId,
        String reply,
        String stopReason,
        TokenUsage usage,
        long elapsedMillis) {
}
