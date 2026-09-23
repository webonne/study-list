package com.study.llmgateway.chat.dto;

/** 流结束。{@code ttftMs} 是首字延迟，{@code elapsedMs} 是整段耗时。 */
public record StreamDone(String finishReason, long ttftMs, long elapsedMs) {
}
