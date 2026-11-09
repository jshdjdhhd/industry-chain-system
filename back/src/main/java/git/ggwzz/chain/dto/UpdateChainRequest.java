package git.ggwzz.chain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新产业链请求（部分更新，null 字段不更新）。
 */
@Data
public class UpdateChainRequest {

    /** 产业链名称 */
    @Size(max = 128, message = "产业链名称不能超过 128 个字符")
    private String chainName;

    /** 产业链描述 */
    @Size(max = 500, message = "产业链描述不能超过 500 个字符")
    private String description;
}
