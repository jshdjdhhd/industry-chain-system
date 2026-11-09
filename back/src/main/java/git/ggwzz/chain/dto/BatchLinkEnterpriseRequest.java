package git.ggwzz.chain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量挂接企业请求 DTO。
 */
@Data
public class BatchLinkEnterpriseRequest {

    /** 企业 ID 列表 */
    @NotEmpty(message = "企业ID列表不能为空")
    private List<@NotNull(message = "企业ID不能为空") Long> enterpriseIds;
}
