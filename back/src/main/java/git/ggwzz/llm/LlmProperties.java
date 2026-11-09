package git.ggwzz.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 通用大模型配置。
 *
 * <p>所有 OpenAI-compatible 厂商共用的基础配置。业务模块可通过继承此类添加专用阈值。</p>
 */
@Data
@Primary
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String provider = "mimo";

    private String baseUrl = "https://api.xiaomimimo.com/v1";

    private String model = "mimo-v2.5-pro-ultraspeed";

    private String apiKey;

    private int timeoutSeconds = 20;

    private double temperature = 0.0D;

    private int maxTokens = 700;
}
