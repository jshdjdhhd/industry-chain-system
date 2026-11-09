package git.ggwzz.chain.vo;

import git.ggwzz.chain.enums.ChainStageType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 产业链树节点 VO。
 *
 * <p>叶子节点的 {@code children} 为空数组（非 null）。</p>
 */
@Data
public class TreeNodeVO {

    /** 节点 ID */
    private Long id;

    /** 节点名称 */
    private String nodeName;

    /** 节点关键词，多个关键词用逗号分隔 */
    private String keywords;

    /** 节点描述 */
    private String description;

    /** 节点层级 */
    private Integer nodeLevel;

    /** 产业链阶段，null 表示普通节点 */
    private ChainStageType stageType;

    /** 父节点 ID，一级节点为 null */
    private Long parentId;

    /** 同级排序 */
    private Integer sortOrder;

    /** 直接关联企业数 */
    private Integer enterpriseCount;

    /** 当前节点直接挂接企业数量 */
    private Integer directEnterpriseCount;

    /** 当前节点及所有下级节点去重后的挂接企业数量 */
    private Integer subtreeEnterpriseCount;

    /** 子节点 */
    private List<TreeNodeVO> children = new ArrayList<>();
}
