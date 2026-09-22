package com.study.llmgateway.chat.store;

import java.util.List;

/**
 * 对话历史存储。
 *
 * <p><b>为什么需要它</b>：大模型 API 是<b>无状态</b>的——服务端不记得上一轮说过什么。
 * 所谓"多轮对话"，是每次请求都把完整历史重新发过去。历史存在哪、怎么裁剪，
 * 完全是你自己的事，这就是本接口存在的原因。
 */
public interface ConversationStore {

    List<Turn> load(String sessionId);

    void append(String sessionId, Turn turn);

    void clear(String sessionId);
}
