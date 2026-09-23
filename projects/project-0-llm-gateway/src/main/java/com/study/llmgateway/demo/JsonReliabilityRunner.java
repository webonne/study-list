package com.study.llmgateway.demo;

import com.study.llmgateway.config.LlmProperties;
import com.study.llmgateway.extract.TicketExtraction;
import com.study.llmgateway.extract.TicketExtractor;
import com.study.llmgateway.llm.LlmClient;
import com.study.llmgateway.llm.dto.ChatCompletionRequest;
import com.study.llmgateway.llm.dto.ChatCompletionResponse;
import com.study.llmgateway.llm.dto.ChatMessage;
import com.study.llmgateway.llm.dto.ResponseFormat;
import com.study.llmgateway.structured.FailureKind;
import com.study.llmgateway.structured.ParseResult;
import com.study.llmgateway.structured.StructuredOutputParser;
import com.study.llmgateway.structured.StructuredProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 任务 #04 的对照实验：<b>同一套提示词</b>，只改"开不开 JSON 模式"这一个变量，各跑 N 次。
 *
 * <p>统计的是<b>第一次调用</b>的结果，不做修复——要测的是模型本身有多守规矩，
 * 修复能救回多少是另一个问题。
 *
 * <p>⚠️ <b>默认关闭，会真实调用 2 × runs 次 API（花钱）</b>。开启：
 * <pre>
 * --app.demo.json-reliability.enabled=true --app.demo.json-reliability.runs=50
 * </pre>
 * 跑完打印对照表，并写一份 CSV 到 {@code target/experiments/}，方便贴进 {@code labs/}。
 */
@Component
@ConditionalOnProperty(name = "app.demo.json-reliability.enabled", havingValue = "true")
public class JsonReliabilityRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JsonReliabilityRunner.class);

    /** 刻意混了几种难度：信息齐全的、缺服务名的、口语化的、带无关细节的。 */
    static final List<String> SAMPLES = List.of(
            "今晚8点开始 order-service 下单接口大量超时，错误日志里全是 HikariPool 连接获取超时，大概三成用户下不了单。",
            "有用户反馈个人中心头像上传偶尔失败，重试一下就好了，不影响其他功能。",
            "支付回调全挂了！！所有订单都卡在待支付，老板在群里问了三遍了",
            "搜索页的推荐商品排序好像不太对，热销的排到后面去了，不急，下周看看就行。",
            "凌晨 3 点 inventory-service 的 pod 反复重启，看 OOMKilled，重启后库存扣减正常，但有几十单出现了超卖。"
    );

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final StructuredProperties structuredProperties;
    private final StructuredOutputParser parser;
    private final int runs;

    public JsonReliabilityRunner(LlmClient llmClient,
                                 LlmProperties llmProperties,
                                 StructuredProperties structuredProperties,
                                 StructuredOutputParser parser,
                                 Environment environment) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.structuredProperties = structuredProperties;
        this.parser = parser;
        this.runs = environment.getProperty("app.demo.json-reliability.runs", Integer.class, 20);
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        log.info("[#04] 对照实验开始：每组 {} 次，共 {} 次 API 调用", runs, runs * 2);
        Tally promptOnly = runGroup("prompt-only", null);
        Tally jsonMode = runGroup("json_object", ResponseFormat.JSON_OBJECT);

        String table = formatTable(List.of(promptOnly, jsonMode));
        log.info("[#04] 对照实验结果（第一次调用、不修复）：\n{}", table);
        Path csv = writeCsv(List.of(promptOnly, jsonMode));
        log.info("[#04] CSV 已写入 {}", csv.toAbsolutePath());
    }

    private Tally runGroup(String name, ResponseFormat format) {
        Tally tally = new Tally(name);
        for (int i = 0; i < runs; i++) {
            String sample = SAMPLES.get(i % SAMPLES.size());
            List<ChatMessage> messages = List.of(
                    ChatMessage.system(TicketExtractor.SYSTEM_PROMPT),
                    ChatMessage.user(sample));
            ChatCompletionRequest request = new ChatCompletionRequest(
                    llmProperties.getModel(), messages, structuredProperties.getMaxTokens(), null, false, format);
            try {
                ChatCompletionResponse response = llmClient.complete(request);
                String raw = response == null ? null : response.firstText().orElse(null);
                String finishReason = response == null ? null : response.finishReason();
                tally.record(parser.parse(raw, finishReason, TicketExtraction.class));
            } catch (Exception e) {
                // 网络 / HTTP 错误和"输出不合格"是两回事，单独计数，不混进失败分类
                tally.callErrors++;
                log.warn("[#04] {} 第 {} 次调用出错：{}", name, i + 1, e.getMessage());
            }
        }
        return tally;
    }

    private static String formatTable(List<Tally> tallies) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s %5s %5s %8s", "组", "次数", "成功", "其中捞回"));
        for (FailureKind kind : FailureKind.values()) {
            sb.append(String.format(" %16s", kind));
        }
        sb.append(String.format(" %8s%n", "调用出错"));
        for (Tally t : tallies) {
            sb.append(String.format("%-12s %5d %5d %8d", t.name, t.total(), t.ok, t.rescued));
            for (FailureKind kind : FailureKind.values()) {
                sb.append(String.format(" %16d", t.failures.get(kind)));
            }
            sb.append(String.format(" %8d%n", t.callErrors));
        }
        return sb.toString();
    }

    private static Path writeCsv(List<Tally> tallies) throws IOException {
        Path dir = Path.of("target", "experiments");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve("json-reliability-" + stamp + ".csv");
        List<String> lines = new ArrayList<>();
        StringBuilder header = new StringBuilder("group,total,ok,rescued");
        for (FailureKind kind : FailureKind.values()) {
            header.append(',').append(kind);
        }
        header.append(",call_errors");
        lines.add(header.toString());
        for (Tally t : tallies) {
            StringBuilder row = new StringBuilder()
                    .append(t.name).append(',').append(t.total()).append(',').append(t.ok).append(',').append(t.rescued);
            for (FailureKind kind : FailureKind.values()) {
                row.append(',').append(t.failures.get(kind));
            }
            row.append(',').append(t.callErrors);
            lines.add(row.toString());
        }
        return Files.write(file, lines);
    }

    static final class Tally {
        final String name;
        int ok;
        int rescued;
        int callErrors;
        final Map<FailureKind, Integer> failures = new EnumMap<>(FailureKind.class);

        Tally(String name) {
            this.name = name;
            for (FailureKind kind : FailureKind.values()) {
                failures.put(kind, 0);
            }
        }

        void record(ParseResult<?> result) {
            if (result.success()) {
                ok++;
                if (result.rescued()) {
                    rescued++;
                }
            } else {
                failures.merge(result.failure(), 1, Integer::sum);
            }
        }

        int total() {
            return ok + callErrors + failures.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
