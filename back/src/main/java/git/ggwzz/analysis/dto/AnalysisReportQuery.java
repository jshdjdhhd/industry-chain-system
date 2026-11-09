package git.ggwzz.analysis.dto;

import lombok.Data;

/**
 * 健康分析报告列表查询参数。
 *
 * <p>支持按 {@code chainId}、{@code status} 过滤，{@code pageNum/pageSize} 分页。</p>
 */
@Data
public class AnalysisReportQuery {

    /** 产业链 ID，可选 */
    private Long chainId;

    /** 任务状态，可选 */
    private String status;

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数，默认 10，最大 100 */
    private Integer pageSize = 10;
}