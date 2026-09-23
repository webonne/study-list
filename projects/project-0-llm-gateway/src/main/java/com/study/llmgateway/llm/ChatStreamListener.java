package com.study.llmgateway.llm;

import java.io.IOException;
import java.io.InputStream;

/**
 * 上游流式响应的回调。{@link #onUpstream} 把响应体交出去，方便客户端断开时关掉它。
 */
public interface ChatStreamListener {

    void onUpstream(InputStream body);

    void onText(String text) throws IOException;

    void onFinish(String finishReason);
}
