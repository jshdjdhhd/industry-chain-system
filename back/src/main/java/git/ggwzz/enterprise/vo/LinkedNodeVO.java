package git.ggwzz.enterprise.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 企业已挂接节点 VO。
 *
 * <p>字段名与前端 domain.ts 中 LinkedNode 接口对齐。</p>
 */
@Data
public class LinkedNodeVO {

    /** 节点 ID */
    private Long nodeId;

    /** 节点名称 */
    private String nodeName;

    /** 节点路径 */
    private String nodePath;

    /** 挂链来源：MANUAL/AI/IMPORT */
    private String source;

    /** 置信度 */
    private BigDecimal confidence;

    /** 挂接时间 */
    private LocalDateTime createdAt;

    /** 节点是否缺失（关系存在但节点已删除） */
    private Boolean nodeMissing;
}
