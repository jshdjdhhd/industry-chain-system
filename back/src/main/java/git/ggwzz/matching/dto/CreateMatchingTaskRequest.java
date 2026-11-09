package git.ggwzz.matching.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建智能挂链推荐任务请求。
 */
@Data
public class CreateMatchingTaskRequest {

    @NotNull(message = "企业ID不能为空")
    private Long enterpriseId;

    @NotNull(message = "产业链ID不能为空")
    private Long chainId;
}
