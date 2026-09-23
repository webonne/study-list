package com.study.llmgateway.chat.dto;

/** 推给浏览器的一个文本增量。 */
public record StreamDelta(String text) {
}
