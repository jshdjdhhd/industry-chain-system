package git.ggwzz.enterprise.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 企业相似度缓存命中投影。
 */
@Data
public class EnterpriseSimilarityCacheRow {

    private Long targetEnterpriseId;

    private BigDecimal similarity;

    private String reasons;
}
