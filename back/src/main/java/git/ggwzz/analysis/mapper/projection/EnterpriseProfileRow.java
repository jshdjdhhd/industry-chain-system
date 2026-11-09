package git.ggwzz.analysis.mapper.projection;

import lombok.Data;

/**
 * 链下挂链企业画像完整性投影（7.1 健康度规则口径）。
 *
 * <p>每行对应一条有效挂链关系（企业去重前），用于在内存按节点路径前缀归并
 * 子树企业质量得分。只携带布尔画像完整性字段，供 7.1 健康度计算使用，
 * 不携带企业画像明细，避免不必要的 GROUP_CONCAT 子查询开销。</p>
 *
 * <p>7.4 报告上下文所需的企业画像明细（名称/状态/地区/行业/主营产品/核心技术/
 * 产值/税收/标签名）请见 {@link EnterpriseProfileDetailRow}，由独立查询
 * {@code selectEnterpriseProfilesByChainIdForReport} 返回。</p>
 */
@Data
public class EnterpriseProfileRow {

    /** 挂链关系对应的企业 ID */
    private Long enterpriseId;

    /** 挂链关系对应的节点 ID */
    private Long nodeId;

    /** 挂链节点路径，如 /5/6/ */
    private String nodePath;

    /** 是否有产值 */
    private Boolean hasOutputValue;

    /** 是否有税收 */
    private Boolean hasTaxAmount;

    /** 是否有标签 */
    private Boolean hasTag;

    /** 是否有主营产品 */
    private Boolean hasMainProducts;

    /** 是否有核心技术 */
    private Boolean hasCoreTech;
}
