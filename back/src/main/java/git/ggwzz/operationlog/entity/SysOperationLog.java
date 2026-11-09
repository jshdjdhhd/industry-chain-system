package git.ggwzz.operationlog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体，对应表 {@code operation_log}（按《04-数据库设计.md》）。
 *
 * <p>由 {@code @OperationLog} 注解 + AOP 统一采集落库。{@code detail} 以 JSON 字符串
 * 写入 JSON 列，记录请求上下文（已脱敏，不含密码/完整 Token）。</p>
 */
@Data
@TableName("operation_log")
public class SysOperationLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 */
    private String operator;

    /** 业务动作 */
    private String action;

    /** 目标类型 */
    private String targetType;

    /** 目标 ID */
    private Long targetId;

    /** 详情 JSON */
    @TableField(value = "detail")
    private String detail;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}