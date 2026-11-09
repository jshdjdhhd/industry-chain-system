package git.ggwzz.chain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 挂链企业请求 DTO。
 */
@Data
public class LinkEnterpriseRequest {

    @NotNull(message = "企业ID不能为空")
    private Long enterpriseId;
}
