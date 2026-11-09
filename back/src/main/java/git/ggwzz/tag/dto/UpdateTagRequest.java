package git.ggwzz.tag.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新标签请求。
 */
@Data
public class UpdateTagRequest {

    /** 标签名称 */
    @Size(max = 64, message = "标签名称不能超过 64 个字符")
    @JsonAlias("name")
    private String tagName;

    /** 标签类型 */
    @Size(max = 32, message = "标签类型不能超过 32 个字符")
    @JsonAlias("type")
    private String tagType;

    /** 颜色 */
    @Size(max = 16, message = "颜色值不能超过 16 个字符")
    private String color;

    /** 标签说明 */
    @Size(max = 255, message = "标签说明不能超过 255 个字符")
    private String description;
}
