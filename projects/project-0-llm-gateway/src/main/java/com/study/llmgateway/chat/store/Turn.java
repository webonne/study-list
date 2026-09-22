package com.study.llmgateway.chat.store;

/**
 * 一轮对话里的一条消息。
 *
 * <p>目前只存纯文本，够 #02 用。等做到 #04 结构化输出、阶段四 tool use 时，
 * 这里要扩展成能存 content block 列表（工具调用和工具结果都不是纯文本）。
 */
public record Turn(Role role, String text) {

    public enum Role {
        USER,
        ASSISTANT
    }

    public static Turn user(String text) {
        return new Turn(Role.USER, text);
    }

    public static Turn assistant(String text) {
        return new Turn(Role.ASSISTANT, text);
    }
}
