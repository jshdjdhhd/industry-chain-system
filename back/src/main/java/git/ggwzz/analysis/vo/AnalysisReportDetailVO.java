package git.ggwzz.analysis.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康分析报告详情 VO。
 *
 * <p>包含任务基础信息与报告内容（Markdown/HTML/PDF 路径）。</p>
 */
@Data
public class AnalysisReportDetailVO {

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

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 报告标题 */
    private String title;

    /** Markdown 源内容 */
    private String contentMarkdown;

    /** HTML 预览内容 */
    private String contentHtml;

    /** PDF 文件路径 */
    private String pdfFilePath;

    /** 健康指标快照（JSON 字符串） */
    private String metricsSnapshot;
}