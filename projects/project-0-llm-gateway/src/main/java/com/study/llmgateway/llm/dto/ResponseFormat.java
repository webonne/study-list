package com.study.llmgateway.llm.dto;

/**
 * {@code response_format} 字段。
 *
 * <p>DeepSeek 在这里只接受 {@code text} 和 {@code json_object}，<b>不接受 {@code json_schema}</b>。
 * 所以 JSON 模式大体能保证"输出是一段合法 JSON"，但<b>不保证字段符合你的结构</b>——
 * 少字段、类型不对、枚举值越界，都要自己校验。
 *
 * <p>使用 {@code json_object} 的三个前提（以 DeepSeek 官方文档当前版本为准）：
 * <ol>
 *   <li>system 或 user 提示词里必须出现 "json" 这个词</li>
 *   <li>最好给一个目标格式的示例</li>
 *   <li>{@code max_tokens} 给够，否则 JSON 会在中途被截断</li>
 * </ol>
 * 另外它偶尔会返回空内容，调用方要能处理。
 */
public record ResponseFormat(String type) {

    public static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object");
}
