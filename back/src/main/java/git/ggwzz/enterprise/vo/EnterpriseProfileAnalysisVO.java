package git.ggwzz.enterprise.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业画像分析结果 VO。
 */
@Data
public class EnterpriseProfileAnalysisVO {

    private Long enterpriseId;

    private String enterpriseName;

    private EnterpriseRadarVO radar;

    private List<SimilarEnterpriseVO> similarEnterprises;

    private LocalDateTime generatedAt;
}
