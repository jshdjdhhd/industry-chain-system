package git.ggwzz.chain.mapper.projection;

import lombok.Data;

/**
 * 节点直接挂接的去重有效企业数投影。
 */
@Data
public class NodeEnterpriseCountRow {

    /** 节点 ID */
    private Long nodeId;

    /** 节点直接挂接的去重有效企业数 */
    private Long directEnterpriseCount;
}
