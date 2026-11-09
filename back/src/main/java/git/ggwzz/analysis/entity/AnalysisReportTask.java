package git.ggwzz.analysis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康分析报告任务实体，对应表 {@code analysis_report_task}。
 *
 * <p>追加式历史表，不做逻辑删除。JSON 列用 {@link JacksonTypeHandler} 映射，
 * 启用 {@code autoResultMap = true}。</p>
 */
@Data
@TableName(value = "analysis_report_task", autoResultMap = true)
public class AnalysisReportTask {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产业链 ID */
    private Long chainId;

    /** 报告类型，对应 {@code AnalysisReportType} 枚举名 */
    private String reportType;

    /** 范围类型，对应 {@code AnalysisScopeType} 枚举名 */
    private String scopeType;

    /** 用户选择节点 ID JSON */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> selectedNodeIds;

    /** 展开子树后的实际节点 ID JSON */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> resolvedNodeIds;

    /** 任务状态，对应 {@code AnalysisReportStatus} 枚举名 */
    private String status;

    /** 重试来源任务 ID */
    private Long retryFromTaskId;

    /** 重试次数 */
    private Integer retryCount;

    /** 请求参数和指标快照（JSON 字符串） */
    private String inputSnapshot;

    /**
     * 失败原因。
     *
     * <p>COMPLETED 时需置空，使用 {@code FieldStrategy.IGNORED} 使 MyBatis-Plus
     * 在 updateById 时即使该字段为 null 也会写入，避免默认 NOT_NULL 策略忽略空值
     * 导致失败任务的 error_message 在重试成功后残留。</p>
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String errorMessage;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}