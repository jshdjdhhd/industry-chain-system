package git.ggwzz.analysis.dto;

import lombok.Data;

/**
 * 健康分析报告可选项。
 *
 * <p>第一版可先简单实现：控制模板报告中是否包含对应小节。
 * 后续接入 LLM 时作为生成提示的开关。</p>
 */
@Data
public class AnalysisReportOptions {

    /** 是否包含企业样本 */
    private Boolean includeEnterpriseSamples;

    /** 是否包含风险节点 */
    private Boolean includeRiskNodes;

    /** 是否包含管理建议 */
    private Boolean includeManagementAdvice;
}