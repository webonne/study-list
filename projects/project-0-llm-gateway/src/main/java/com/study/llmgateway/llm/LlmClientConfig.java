package com.study.llmgateway.llm;

import com.study.llmgateway.config.LlmProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 直接用 {@link RestClient} 调 OpenAI 兼容接口，不引第三方 LLM SDK。
 *
 * <p><b>为什么不用 SDK / 框架</b>：阶段一的目标就是看清协议本身——
 * messages 数组长什么样、usage 里有哪些字段、finish_reason 有哪些取值。
 * SDK 恰好会把这些全藏起来。等阶段二需要 VectorStore 抽象时，再引 Spring AI 不迟。
 *
 * <p>副产品：不绑定任何厂商 SDK，换成别的 OpenAI 兼容服务只改配置。
 */
@Configuration
public class LlmClientConfig {

    @Bean
    public RestClient llmRestClient(LlmProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                // ⚠️ JDK 的 HttpClient 默认用 HTTP/2。走 https 时靠 ALPN 协商没问题，
                // 但明文 http 下它会尝试 h2c 升级，很多简单服务端（本地 mock、
                // Ollama、部分自建网关）直接读不懂，表现为诡异的
                // "header parser received no bytes" / EOFException。
                // 固定 HTTP/1.1 最省心，对 LLM 这种长连接少、单请求大的场景也没什么损失。
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        // 密钥放哪个头由配置决定：OpenAI 兼容接口用 Bearer，少数网关用 x-api-key。
        // key 为空也照常启动——发请求时才失败，方便先把服务跑起来看看。
        String apiKey = properties.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            if ("api-key".equalsIgnoreCase(properties.getAuth())) {
                builder.defaultHeader("x-api-key", apiKey);
            } else {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
        }
        return builder.build();
    }
}
