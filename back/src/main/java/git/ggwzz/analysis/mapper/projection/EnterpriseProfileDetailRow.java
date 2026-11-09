package git.ggwzz.analysis.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 链下挂链企业画像明细投影（7.4 报告上下文口径）。
 *
 * <p>每行对应一条有效挂链关系，携带报告上下文所需的企业画像明细字段
 * （名称/状态/地区/行业/主营产品/核心技术/产值/税收/标签名），供
 * {@link git.ggwzz.analysis.service.AnalysisReportContextBuilder} 组装企业样本使用。
 * 与 {@link EnterpriseProfileRow} 分离：7.1 健康度计算只需布尔完整性字段，
 * 走轻量查询；7.4 报告需要明细，走独立查询（含 GROUP_CONCAT 标签名子查询），
 * 二者职责清晰，互不影响。</p>
 */
@Data
public class EnterpriseProfileDetailRow {

    /** 挂链关系对应的企业 ID */
    private Long enterpriseId;

    /** 挂链关系对应的节点 ID */
    private Long nodeId;

    /** 企业名称 */
    private String enterpriseName;

    /** 企业状态 */
    private String status;

    /** 所在地区 */
    private String region;

    /** 所属行业 */
    private String industry;

    /** 主营产品 */
    private String mainProducts;

    /** 核心技术 */
    private String coreTech;

    /** 产值（万元） */
    private BigDecimal outputValue;

    /** 税收（万元） */
    private BigDecimal taxAmount;

    /** 标签名称集合（逗号分隔），无标签为 null */
    private String tagNames;
}
