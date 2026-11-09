package git.ggwzz.enterprise.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导入记录 VO
 */
@Data
public class ImportRecordVO {

    /** 主键 */
    private Long id;

    /** 文件名称 */
    private String fileName;

    /** 导入时间 */
    private LocalDateTime importedAt;

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

    /** 失败明细 */
    private List<ImportResultVO.FailureItem> failures;
}
