package git.ggwzz.matching.vo;

import lombok.Data;

/**
 * 人工确认挂链视图对象。
 */
@Data
public class ManualConfirmVO {

    private Long enterpriseId;
    private Long nodeId;
    private String source;
    private Boolean linked;
}
