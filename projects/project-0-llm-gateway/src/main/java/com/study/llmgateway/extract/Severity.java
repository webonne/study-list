package com.study.llmgateway.extract;

/**
 * 故障级别。P0 最严重。
 *
 * <p>模型输出 "p1" 也能认，靠的是解析器里开的 {@code ACCEPT_CASE_INSENSITIVE_ENUMS}，
 * 不是写在这里的注解。实测过：Jackson 2.19 下，把
 * {@code @JsonFormat(with = ACCEPT_CASE_INSENSITIVE_VALUES / _PROPERTIES)}
 * 写在枚举类型或 record 字段上都<b>不生效</b>。见 StructuredOutputParser。
 */
public enum Severity {
    P0, P1, P2, P3
}
