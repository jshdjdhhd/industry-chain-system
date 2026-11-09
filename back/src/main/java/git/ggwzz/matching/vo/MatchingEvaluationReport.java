package git.ggwzz.matching.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能挂链离线评估报告。
 */
@Data
public class MatchingEvaluationReport {

    private String providerName;

    private Long chainId;

    private Integer topK;

    private Integer sampleCount;

    private BigDecimal recallAtK;

    private BigDecimal top1Accuracy;

    private BigDecimal meanReciprocalRank;

    private Integer fallbackCount;

    private List<Miss> misses = new ArrayList<>();

    @Data
    public static class Miss {

        private Long enterpriseId;

        private Long expectedNodeId;

        private String sampleName;

        private List<Long> returnedNodeIds = new ArrayList<>();
    }
}
