package git.ggwzz.chain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产业链响应 VO。
 */
@Data
public class ChainVO {

    /** 产业链 ID */
    private Long id;

    /** 产业链名称 */
    private String chainName;

    /** 产业链描述 */
    private String description;

    /** 未删除节点数 */
    private Long nodeCount;

    /** 去重后的未删除企业数 */
    private Long enterpriseCount;

    /** 创建时间 */
    private LocalDateTime createTime;
}
