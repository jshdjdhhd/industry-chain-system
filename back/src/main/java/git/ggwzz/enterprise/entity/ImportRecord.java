package git.ggwzz.enterprise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业导入记录实体，对应表 {@code import_record}。
 */
@Data
@TableName("import_record")
public class ImportRecord {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件名称 */
    private String fileName;

    /** 操作人 */
    private String operator;

    /** 总行数 */
    private Integer totalCount;

    /** 成功数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failureCount;

    /** 状态：SUCCESS/PARTIAL_FAILED/FAILED */
    private String status;

    /** 失败明细JSON */
    private String failureDetail;

    /** 导入时间 */
    private LocalDateTime importedAt;
}
