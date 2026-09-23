package com.study.llmgateway.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiSseDecoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsDeltasAndStopsAtDone() throws IOException {
        String sse = """
                : keep-alive

                data: {"choices":[{"delta":{"role":"assistant","content":"你"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"好"},"finish_reason":null}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                data: {"choices":[{"delta":{"content":"不该出现"}}]}
                """;
        List<String> texts = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        OpenAiSseDecoder.decode(new BufferedReader(new StringReader(sse)), objectMapper, () -> false,
                new ChatStreamListener() {
                    @Override
                    public void onUpstream(java.io.InputStream body) {
                    }

                    @Override
                    public void onText(String text) {
                        texts.add(text);
                    }

                    @Override
                    public void onFinish(String finishReason) {
                        reasons.add(finishReason);
                    }
                });

        assertEquals(List.of("你", "好"), texts);
        assertEquals(List.of("stop"), reasons);
    }

    @Test
    void stopsWhenCancelled() throws IOException {
        String sse = """
                data: {"choices":[{"delta":{"content":"一"}}]}

                data: {"choices":[{"delta":{"content":"二"}}]}
                """;
        List<String> texts = new ArrayList<>();

        OpenAiSseDecoder.decode(new BufferedReader(new StringReader(sse)), objectMapper, () -> true,
                new ChatStreamListener() {
                    @Override
                    public void onUpstream(java.io.InputStream body) {
                    }

                    @Override
                    public void onText(String text) {
                        texts.add(text);
                    }

                    @Override
                    public void onFinish(String finishReason) {
                    }
                });

        assertEquals(List.of(), texts);
    }
}
