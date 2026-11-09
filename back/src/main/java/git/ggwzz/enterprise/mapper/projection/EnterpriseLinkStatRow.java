package git.ggwzz.enterprise.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 企业挂链统计投影。
 */
@Data
public class EnterpriseLinkStatRow {

    private Long enterpriseId;

    private Integer linkedNodeCount;

    private BigDecimal maxConfidence;

    private Boolean hasManualLink;
}
