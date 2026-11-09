package git.ggwzz.enterprise.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 相似企业推荐 VO。
 */
@Data
public class SimilarEnterpriseVO {

    private Long enterpriseId;

    private String enterpriseName;

    private BigDecimal similarity;

    private Integer similarityPercent;

    private String industry;

    private String mainProducts;

    private List<String> reasons;
}
