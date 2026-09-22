package com.study.llmgateway.chat.dto;

public record ChatResponse(
        String sessionId,
        String reply,
        String finishReason,
        TokenUsage usage,
        long elapsedMillis) {
}
