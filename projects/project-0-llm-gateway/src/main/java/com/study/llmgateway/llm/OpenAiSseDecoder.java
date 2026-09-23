package com.study.llmgateway.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.llmgateway.llm.dto.ChatCompletionChunk;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/**
 * 解析 OpenAI 兼容的流式响应：一行一个 {@code data: {...}}，以 {@code data: [DONE]} 结束。
 *
 * <p>本项目不引厂商 SDK，所以没有 Anthropic 的 {@code StreamResponse}。
 * 上游自己就是一条 SSE，这里逐行把增量文本拆出来。
 */
public final class OpenAiSseDecoder {

    private OpenAiSseDecoder() {
    }

    public static void decode(BufferedReader reader,
                               ObjectMapper objectMapper,
                               BooleanSupplier cancelled,
                               ChatStreamListener listener) throws IOException {
        String line;
        while (!cancelled.getAsBoolean() && (line = reader.readLine()) != null) {
            if (line.isBlank() || line.startsWith(":")) {
                continue;
            }
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring("data:".length()).trim();
            if ("[DONE]".equals(data)) {
                return;
            }
            ChatCompletionChunk chunk = objectMapper.readValue(data, ChatCompletionChunk.class);
            String text = chunk.deltaText();
            if (text != null && !text.isEmpty()) {
                listener.onText(text);
            }
            String finishReason = chunk.finishReason();
            if (finishReason != null && !finishReason.isBlank()) {
                listener.onFinish(finishReason);
            }
        }
    }
}
