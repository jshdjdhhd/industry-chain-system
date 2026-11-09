package git.ggwzz.chain.vo;

import lombok.Data;

/**
 * 节点企业数量统计 VO。
 */
@Data
public class EnterpriseCountVO {

    /** 节点 ID */
    private Long nodeId;

    /** 节点名称 */
    private String nodeName;

    /** 直接关联企业数 */
    private Integer directEnterpriseCount;

    /** 含所有下级节点的总企业数 */
    private Integer totalEnterpriseCount;
}
