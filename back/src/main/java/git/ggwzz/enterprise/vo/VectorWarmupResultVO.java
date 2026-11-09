package git.ggwzz.enterprise.vo;

import lombok.Data;

/**
 * 向量预热结果 VO。
 */
@Data
public class VectorWarmupResultVO {

    private int totalCount;

    private int successCount;

    private int skippedCount;

    private int failureCount;
}
