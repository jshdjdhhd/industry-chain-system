package git.ggwzz.matching.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智能挂链 embedding 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "matching.embedding")
public class EmbeddingProperties {

    private int timeoutSeconds = 30;

    private String endpoint = "http://localhost:11434/v1/embeddings";

    private String model = "qwen3-embedding:0.6b";

    private String apiKey;

    private int dimension = 1024;

    private Hybrid hybrid = new Hybrid();

    @Data
    public static class Hybrid {
        private double vectorWeight = 0.70D;
        private double ruleWeight = 0.30D;
        private double minVectorScore = 0.25D;
        private int topK = 5;
        private double ruleOnlyFastPathConfidence = 0.95D;
        private double ruleOnlyFastPathGap = 0.20D;
    }
}
