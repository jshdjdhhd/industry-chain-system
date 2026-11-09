package git.ggwzz.analysis.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 产业链诊断树节点 VO。
 *
 * <p>每个节点携带健康分、健康等级、中文标签及弱项原因。
 * 叶子节点的 {@code children} 为空数组（非 null）。</p>
 */
@Data
public class DiagnosisTreeNodeVO {

    /** 节点 ID，虚拟根使用产业链 ID */
    private Long nodeId;

    /** 节点名称，虚拟根使用产业链名称 */
    private String nodeName;

    /** 物化路径，如 /5/6/7/；虚拟根为 null */
    private String nodePath;

    /** 节点层级，一级=1，虚拟根=0 */
    private Integer nodeLevel;

    /** 节点关键词 */
    private String keywords;

    /** 节点描述 */
    private String description;

    /** 当前节点直接挂接企业数 */
    private Integer directEnterpriseCount;

    /** 当前节点及所有下级节点去重后的挂接企业数 */
    private Integer subtreeEnterpriseCount;

    /** 健康分（0-100） */
    private Integer healthScore;

    /** 健康等级，对应 {@code ChainHealthLevel} 枚举的 name() */
    private String healthLevel;

    /** 健康等级中文标签 */
    private String healthLabel;

    /** 弱项原因列表 */
    private List<String> weakReasons = new ArrayList<>();

    /** 子节点 */
    private List<DiagnosisTreeNodeVO> children = new ArrayList<>();
}
