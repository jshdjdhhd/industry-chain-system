package git.ggwzz.chain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import git.ggwzz.chain.enums.ChainStageType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产业链节点实体，对应表 {@code chain_node}。
 *
 * <p>自关联树形：{@code parent_id} + {@code node_path}（如 /5/6/7/）。</p>
 */
@Data
@TableName("chain_node")
public class ChainNode {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属产业链 ID */
    private Long chainId;

    /** 父节点 ID，NULL 为一级节点 */
    private Long parentId;

    /** 节点名称 */
    private String nodeName;

    /** 节点关键词，多个关键词用逗号分隔 */
    private String keywords;

    /** 节点层级，一级=1 */
    private Integer nodeLevel;

    /** 节点描述 */
    private String description;

    /** 节点路径，如 /5/6/7/，用于子树查询 */
    private String nodePath;

    /** 同级排序 */
    private Integer sortOrder;

    /** 产业链阶段，null 表示普通节点 */
    private ChainStageType stageType;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
