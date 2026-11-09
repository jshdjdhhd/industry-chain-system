package git.ggwzz.enterprise.dto;

import lombok.Data;

import java.util.List;

/**
 * 导入结果 VO
 */
@Data
public class ImportResultVO {

    /** 成功数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failureCount;

    /** 失败明细 */
    private List<FailureItem> failures;

    @Data
    public static class FailureItem {
        /** 行号 */
        private Integer rowNumber;
        /** 企业名称 */
        private String enterpriseName;
        /** 失败原因 */
        private String reason;
    }
}
