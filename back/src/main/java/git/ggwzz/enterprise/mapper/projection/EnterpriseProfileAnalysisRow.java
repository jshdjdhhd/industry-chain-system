package git.ggwzz.enterprise.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 企业画像分析基础投影。
 */
@Data
public class EnterpriseProfileAnalysisRow {

    private Long id;

    private String enterpriseName;

    private String status;

    private String industry;

    private String businessScope;

    private String mainProducts;

    private String coreTech;

    private String keywords;

    private String patentInfo;

    private String bidInfo;

    private BigDecimal outputValue;

    private BigDecimal taxAmount;
}
