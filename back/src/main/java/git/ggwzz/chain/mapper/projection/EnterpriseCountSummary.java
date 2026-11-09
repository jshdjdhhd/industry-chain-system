package git.ggwzz.chain.mapper.projection;

import lombok.Data;

/**
 * 节点直接去重有效企业数和子树去重有效企业总数投影。
 */
@Data
public class EnterpriseCountSummary {

    /** 当前节点直接挂接的去重有效企业数 */
    private Long directEnterpriseCount;

    /** 当前节点及全部后代节点中的去重有效企业数 */
    private Long totalEnterpriseCount;
}
