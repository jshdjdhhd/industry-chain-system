package git.ggwzz.chain.vo;

import git.ggwzz.chain.enums.ChainStageType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产业链节点响应 VO。
 */
@Data
public class NodeVO {

    /** 节点 ID */
    private Long id;

    /** 所属产业链 ID */
    private Long chainId;

    /** 父节点 ID */
    private Long parentId;

    /** 节点名称 */
    private String nodeName;

    /** 节点关键词，多个关键词用逗号分隔 */
    private String keywords;

    /** 节点描述 */
    private String description;

    /** 节点层级 */
    private Integer nodeLevel;

    /** 节点路径 */
    private String nodePath;

    /** 同级排序 */
    private Integer sortOrder;

    /** 产业链阶段，null 表示普通节点 */
    private ChainStageType stageType;

    /** 创建时间 */
    private LocalDateTime createTime;
}
