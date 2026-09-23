package com.study.llmgateway.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.llmgateway.config.LlmProperties;
import com.study.llmgateway.llm.dto.ChatCompletionRequest;
import com.study.llmgateway.llm.dto.ChatCompletionResponse;
import com.study.llmgateway.llm.dto.ChatMessage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 对 {@code POST /chat/completions} 的最小封装。
 *
 * <p>后续任务在这里接着长：
 * <ul>
 *   <li><b>#04 结构化输出</b>：请求体加 {@code response_format}</li>
 *   <li><b>#06 错误处理</b>：{@code .onStatus(...)} 按状态码分类，429 要读退避信息</li>
 * </ul>
 *
 * <p>⚠️ 和官方 SDK 不同，{@link RestClient} <b>没有内置重试</b>。
 * 这不是缺陷，是 #06 留给你的活——自己写一遍，你才会真正想清楚
 * "哪些错误该重试、退避多久、总耗时上限是多少"。
 */
@Component
public class LlmClient {

    private final RestClient restClient;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    public LlmClient(RestClient llmRestClient, LlmProperties properties, ObjectMapper objectMapper) {
        this.restClient = llmRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ChatCompletionResponse complete(List<ChatMessage> messages) {
        ChatCompletionRequest request = ChatCompletionRequest.of(
                properties.getModel(), messages, properties.getMaxTokens());

        return restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
    }

    /**
     * 流式调用。请求体 {@code stream=true}，响应是 SSE。
     * {@code cancelled} 变为 true，或 {@link ChatStreamListener#onUpstream} 拿到的流被关掉时，停止读取。
     */
    public void stream(List<ChatMessage> messages, BooleanSupplier cancelled, ChatStreamListener listener)
            throws IOException {
        ChatCompletionRequest request = ChatCompletionRequest.streaming(
                properties.getModel(), messages, properties.getMaxTokens());

        restClient.post()
                .uri("/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().isError()) {
                        String err = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new IllegalStateException("upstream " + response.getStatusCode().value() + ": " + abbreviate(err));
                    }
                    listener.onUpstream(response.getBody());
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        OpenAiSseDecoder.decode(reader, objectMapper, cancelled, listener);
                    }
                    return null;
                });
    }

    private static String abbreviate(String text) {
        if (text == null || text.length() <= 500) {
            return text;
        }
        return text.substring(0, 500);
    }
}
