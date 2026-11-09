package git.ggwzz.matching.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 拒绝推荐结果请求。
 */
@Data
public class RejectMatchingResultRequest {

    @NotBlank(message = "拒绝原因不能为空")
    private String rejectReason;
}
