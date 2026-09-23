package com.study.llmgateway.extract;

import com.study.llmgateway.structured.StructuredOutputService;
import com.study.llmgateway.structured.StructuredResult;
import org.springframework.stereotype.Service;

/**
 * #04 的用例：把一段随手写的故障描述，抽成 {@link TicketExtraction}。
 *
 * <p>系统提示是<b>固定不变</b>的（不拼任何变量），用户输入放在 user 消息里——
 * 这样每次请求的前缀都一样，#05 的缓存能命中。
 */
@Service
public class TicketExtractor {

    /**
     * 写法上的几个要点：
     * <ul>
     *   <li>出现 "json" 这个词：DeepSeek 的 JSON 模式要求</li>
     *   <li>给一个完整示例：比一长串字段说明管用</li>
     *   <li>每个字段写清楚取值规则，尤其是"缺信息时填什么"——
     *       不写的话模型会自己编，或者干脆省掉这个字段</li>
     * </ul>
     */
    public static final String SYSTEM_PROMPT = """
            你是一个工单信息抽取器。从用户给出的故障描述中抽取信息，只输出一个 json 对象，不要输出任何其他文字。

            json 格式示例：
            {"title":"订单服务下单接口超时","severity":"P1","component":"order-service","summary":"晚高峰下单接口大量超时，影响部分用户下单","steps":["查看 order-service 最近一小时的错误日志","检查数据库连接池是否耗尽"]}

            字段规则：
            - title：一句话标题，不超过 30 个字
            - severity：只能是 P0、P1、P2、P3 之一。P0 = 核心功能全面不可用；P1 = 核心功能部分受损；P2 = 非核心功能受损；P3 = 轻微问题
            - component：受影响的服务名。描述里没有提到时填 "unknown"，不要猜
            - summary：一句话概述影响
            - steps：排查或复现步骤，至少 1 条
            """;

    private final StructuredOutputService structuredOutput;

    public TicketExtractor(StructuredOutputService structuredOutput) {
        this.structuredOutput = structuredOutput;
    }

    public StructuredResult<TicketExtraction> extract(String description) {
        return structuredOutput.generate(SYSTEM_PROMPT, description, TicketExtraction.class);
    }
}
