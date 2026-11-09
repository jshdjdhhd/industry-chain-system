package git.ggwzz.chain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点下企业列表 VO。
 */
@Data
public class NodeEnterpriseVO {

    /** 企业ID */
    private Long enterpriseId;

    /** 企业名称，由关联 enterprise 表查询填充 */
    private String enterpriseName;

    /** 来源（MANUAL/AI/IMPORT） */
    private String source;

    /** 挂链时间 */
    private LocalDateTime createTime;
}
