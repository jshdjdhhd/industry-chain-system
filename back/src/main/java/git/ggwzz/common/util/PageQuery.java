package git.ggwzz.common.util;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询基类。
 *
 * <p>统一使用 {@code pageNum / pageSize}，禁止混用 {@code page/size}。
 * {@code pageNum} 从 1 开始，{@code pageSize} 默认 10、最大 100。</p>
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum = 1;

    /** 每页条数，默认 10，最大 100 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer pageSize = 10;
}