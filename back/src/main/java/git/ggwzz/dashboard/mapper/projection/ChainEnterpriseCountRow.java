package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

/**
 * 产业链企业分布聚合投影。
 */
@Data
public class ChainEnterpriseCountRow {

    /** 产业链 ID */
    private Long chainId;

    /** 产业链名称 */
    private String chainName;

    /** 产业链下的企业数 */
    private long enterpriseCount;
}
