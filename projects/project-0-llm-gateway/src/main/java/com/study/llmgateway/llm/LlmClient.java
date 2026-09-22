package com.study.llmgateway.llm;

import com.study.llmgateway.llm.dto.ChatCompletionRequest;
import com.study.llmgateway.llm.dto.ChatCompletionResponse;
import com.study.llmgateway.llm.dto.ChatMessage;
import com.study.llmgateway.config.LlmProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 对 {@code POST /chat/completions} 的最小封装。
 *
 * <p>后续任务在这里接着长：
 * <ul>
 *   <li><b>#03 流式</b>：新增 {@code streamCompletion()}，请求体里 {@code stream=true}，
 *       按 SSE 逐行解析 {@code data: {...}}，遇到 {@code data: [DONE]} 结束</li>
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

    public LlmClient(RestClient llmRestClient, LlmProperties properties) {
        this.restClient = llmRestClient;
        this.properties = properties;
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
}
