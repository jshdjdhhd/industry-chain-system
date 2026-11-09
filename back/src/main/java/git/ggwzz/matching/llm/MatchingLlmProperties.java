package git.ggwzz.matching.llm;

import git.ggwzz.llm.LlmProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智能挂链专用大模型配置。
 *
 * <p>继承通用 LlmProperties，添加挂链重排序所需的阈值参数。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Component
@ConfigurationProperties(prefix = "matching.llm")
public class MatchingLlmProperties extends LlmProperties {

    private int candidateTopK = 6;

    private int resultTopK = 3;

    private double skipRerankConfidenceThreshold = 0.82D;

    private double minResultConfidence = 0.65D;

    private double clearWinnerMinConfidenceThreshold = 0.70D;

    private double clearWinnerGapThreshold = 0.08D;

    private double strongRuleConfidenceThreshold = 0.90D;

    private double strongEvidenceMinVectorScore = 0.55D;

    private int analysisTopK = 3;
}
