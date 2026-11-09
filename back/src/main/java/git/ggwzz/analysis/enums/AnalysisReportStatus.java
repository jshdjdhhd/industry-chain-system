package git.ggwzz.analysis.enums;

import java.util.Locale;

/**
 * 健康分析报告任务状态。
 *
 * <p>状态流转：CREATED → PROCESSING → COMPLETED/FAILED；FAILED/CANCELLED 可重试。
 * 报告任务表为追加式历史，不做逻辑删除。</p>
 */
public enum AnalysisReportStatus {

    /** 已创建（初值，一般很快进入 PROCESSING） */
    CREATED,
    /** 处理中 */
    PROCESSING,
    /** 已完成 */
    COMPLETED,
    /** 已失败 */
    FAILED,
    /** 已取消 */
    CANCELLED;

    /**
     * 安全解析状态字符串，非法值返回 null。
     *
     * @param status 状态字符串
     * @return 状态枚举，非法返回 null
     */
    public static AnalysisReportStatus from(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AnalysisReportStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 判断当前状态是否允许重试。
     *
     * @return 仅 FAILED、CANCELLED 允许重试
     */
    public boolean isRetryable() {
        return this == FAILED || this == CANCELLED;
    }
}
