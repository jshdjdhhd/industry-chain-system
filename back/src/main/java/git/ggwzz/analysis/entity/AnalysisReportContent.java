package git.ggwzz.analysis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康分析报告内容实体，对应表 {@code analysis_report_content}。
 *
 * <p>与任务 1:1 关联（{@code uk_analysis_report_content_task_id}）。
 * 第一版 {@code contentHtml}、{@code pdfFilePath} 暂可置空（7.5 实现）。</p>
 */
@Data
@TableName("analysis_report_content")
public class AnalysisReportContent {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报告任务 ID */
    private Long taskId;

    /** 报告标题 */
    private String title;

    /** Markdown 源内容 */
    private String contentMarkdown;

    /** HTML 预览内容（7.5 实现 Markdown→HTML 转换） */
    private String contentHtml;

    /** PDF 文件路径（7.5 实现） */
    private String pdfFilePath;

    /** 健康指标快照（JSON 字符串） */
    private String metricsSnapshot;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}