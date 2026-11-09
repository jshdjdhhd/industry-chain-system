package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

/**
 * 根节点子树企业统计投影。
 */
@Data
public class NodeSubtreeCountRow {

    /** 根节点 ID */
    private Long nodeId;

    /** 根节点名称 */
    private String nodeName;

    /** 子树内去重企业数 */
    private long enterpriseCount;
}
