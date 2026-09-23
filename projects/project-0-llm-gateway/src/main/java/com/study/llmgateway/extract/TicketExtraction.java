package com.study.llmgateway.extract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 从一段故障描述里抽取出的工单。
 *
 * <p>这个类就是"结构"的唯一真相：提示词里的格式示例、解析、校验，都以它为准。
 * 改字段时三处要一起改——提示词在 {@link TicketExtractor}。
 *
 * <p>对模型输出的原则：<b>格式上能宽容就宽容，语义上一点不让</b>。
 * <ul>
 *   <li>宽容：枚举不分大小写（"p1" 也认，见 {@link Severity}）、多出来的字段直接忽略</li>
 *   <li>严格：{@code @NotBlank} 等注解，空串、空列表、缺字段都不放行</li>
 * </ul>
 */
public record TicketExtraction(
        @NotBlank @Size(max = 30) String title,
        @NotNull Severity severity,
        @NotBlank String component,
        @NotBlank String summary,
        @NotEmpty List<@NotBlank String> steps) {
}
