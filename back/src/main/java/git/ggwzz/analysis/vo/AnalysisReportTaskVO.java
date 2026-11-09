package git.ggwzz.analysis.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康分析报告任务 VO（列表与基础详情共用）。
 */
@Data
public class AnalysisReportTaskVO {

    /** 任务 ID */
    private Long id;

    /** 产业链 ID */
    private Long chainId;

    /** 产业链名称 */
    private String chainName;

    /** 报告类型 */
    private String reportType;

    /** 范围类型 */
    private String scopeType;

    /** 用户选择节点 ID 列表 */
    private List<Long> selectedNodeIds;

    /** 展开子树后的实际节点 ID 列表 */
    private List<Long> resolvedNodeIds;

    /** 任务状态 */
    private String status;

    /** 重试来源任务 ID */
    private Long retryFromTaskId;

    /** 重试次数 */
    private Integer retryCount;

    /** 失败原因 */
    private String errorMessage;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}