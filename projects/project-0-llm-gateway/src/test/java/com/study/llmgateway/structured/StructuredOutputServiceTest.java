package com.study.llmgateway.structured;

import com.study.llmgateway.extract.TicketExtraction;
import com.study.llmgateway.llm.LlmClient;
import com.study.llmgateway.llm.dto.ChatCompletionResponse;
import com.study.llmgateway.llm.dto.ChatMessage;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StructuredOutputServiceTest {

    private static final String PROMPT = "只输出 json 对象";
    private static final String VALID = """
            {"title":"下单接口超时","severity":"P1","component":"order-service",
             "summary":"三成用户下不了单","steps":["看错误日志"]}""";

    private LlmClient llmClient;
    private StructuredOutputService service;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        StructuredProperties properties = new StructuredProperties();
        properties.setMaxRepairAttempts(1);
        StructuredOutputParser parser = new StructuredOutputParser(
                Validation.buildDefaultValidatorFactory().getValidator());
        service = new StructuredOutputService(llmClient, parser, properties);
    }

    @Test
    void firstTrySucceeds() {
        when(llmClient.completeJson(anyList(), anyInt())).thenReturn(reply(VALID, "stop"));

        StructuredResult<TicketExtraction> result = service.generate(PROMPT, "下单超时", TicketExtraction.class);

        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void repairSendsBackPreviousOutputAndReason() {
        String bad = VALID.replace("\"P1\"", "\"紧急\"");
        when(llmClient.completeJson(anyList(), anyInt()))
                .thenReturn(reply(bad, "stop"))
                .thenReturn(reply(VALID, "stop"));

        StructuredResult<TicketExtraction> result = service.generate(PROMPT, "下单超时", TicketExtraction.class);

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.failures()).containsExactly(FailureKind.INVALID_VALUE);

        // 修复请求里必须带着：模型上一次的输出 + 哪里不对。原样重发没有意义
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(2)).completeJson(captor.capture(), anyInt());
        List<ChatMessage> repair = captor.getAllValues().get(1);
        assertThat(repair).hasSize(4);
        assertThat(repair.get(2)).isEqualTo(ChatMessage.assistant(bad));
        assertThat(repair.get(3).content()).contains("INVALID_VALUE").contains("severity");
    }

    @Test
    void truncationIsNotRetried() {
        when(llmClient.completeJson(anyList(), anyInt())).thenReturn(reply("{\"title\":\"下", "length"));

        assertThatThrownBy(() -> service.generate(PROMPT, "下单超时", TicketExtraction.class))
                .isInstanceOf(StructuredOutputException.class)
                .satisfies(e -> {
                    StructuredOutputException ex = (StructuredOutputException) e;
                    assertThat(ex.kind()).isEqualTo(FailureKind.TRUNCATED);
                    assertThat(ex.attempts()).isEqualTo(1);
                });
        verify(llmClient, times(1)).completeJson(anyList(), anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyIsRetriedWithoutRepairMessage() {
        when(llmClient.completeJson(anyList(), anyInt()))
                .thenReturn(reply("", "stop"))
                .thenReturn(reply(VALID, "stop"));

        StructuredResult<TicketExtraction> result = service.generate(PROMPT, "下单超时", TicketExtraction.class);

        assertThat(result.attempts()).isEqualTo(2);
        // 空内容没有"上一次的输出"可以指出错误，第二次请求和第一次一样
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(2)).completeJson(captor.capture(), anyInt());
        assertThat(captor.getAllValues().get(1)).hasSize(2);
    }

    @Test
    void givesUpAfterRepairAttemptsExhausted() {
        when(llmClient.completeJson(anyList(), anyInt())).thenReturn(reply("不是 JSON", "stop"));

        assertThatThrownBy(() -> service.generate(PROMPT, "下单超时", TicketExtraction.class))
                .isInstanceOf(StructuredOutputException.class)
                .satisfies(e -> {
                    StructuredOutputException ex = (StructuredOutputException) e;
                    assertThat(ex.attempts()).isEqualTo(2);
                    assertThat(ex.failures()).containsExactly(FailureKind.NOT_JSON, FailureKind.NOT_JSON);
                });
    }

    @Test
    void rejectsPromptWithoutJsonKeyword() {
        assertThatThrownBy(() -> service.generate("只输出一个对象", "下单超时", TicketExtraction.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("json");
    }

    private static ChatCompletionResponse reply(String content, String finishReason) {
        return new ChatCompletionResponse("id", "m",
                List.of(new ChatCompletionResponse.Choice(0, ChatMessage.assistant(content), finishReason)),
                null);
    }
}
