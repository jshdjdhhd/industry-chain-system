package git.ggwzz.enterprise.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 绑定标签请求。
 */
@Data
public class BindTagRequest {

    /** 标签 ID */
    @NotNull(message = "标签ID不能为空")
    private Long tagId;
}
