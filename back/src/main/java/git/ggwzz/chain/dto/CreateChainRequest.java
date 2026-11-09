package git.ggwzz.chain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建产业链请求。
 */
@Data
public class CreateChainRequest {

    /** 产业链名称 */
    @NotBlank(message = "产业链名称不能为空")
    @Size(max = 128, message = "产业链名称不能超过 128 个字符")
    private String chainName;

    /** 产业链描述 */
    @Size(max = 500, message = "产业链描述不能超过 500 个字符")
    private String description;
}
