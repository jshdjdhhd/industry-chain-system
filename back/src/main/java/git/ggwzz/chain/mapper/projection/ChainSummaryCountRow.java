package git.ggwzz.chain.mapper.projection;

import lombok.Data;

/**
 * 产业链汇总数量投影。
 */
@Data
public class ChainSummaryCountRow {

    /** 产业链 ID */
    private Long chainId;

    /** 有效节点数 */
    private Long nodeCount;

    /** 有效企业数 */
    private Long enterpriseCount;
}
