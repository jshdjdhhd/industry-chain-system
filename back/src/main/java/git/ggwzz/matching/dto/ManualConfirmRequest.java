package git.ggwzz.matching.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 人工确认挂链请求。
 */
@Data
public class ManualConfirmRequest {

    @NotNull(message = "企业ID不能为空")
    private Long enterpriseId;

    @NotNull(message = "节点ID不能为空")
    private Long nodeId;

    private Long taskId;

    private Long sourceResultId;

    private String reason;
}
