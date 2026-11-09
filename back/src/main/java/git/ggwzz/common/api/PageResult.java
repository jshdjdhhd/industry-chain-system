package git.ggwzz.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应封装。
 *
 * <p>响应字段统一为 {@code records / total / pageNum / pageSize}，
 * 禁止用 {@code list} 替代 {@code records}，禁止混用 {@code page/size} 与 {@code pageNum/pageSize}。</p>
 *
 * @param <T> 记录类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页记录 */
    private List<T> records;
    /** 总记录数 */
    private long total;
    /** 当前页码，从 1 开始 */
    private long pageNum;
    /** 每页条数 */
    private long pageSize;

    /**
     * 从 MyBatis-Plus 分页结果构造统一分页响应。
     *
     * @param page MyBatis-Plus 分页结果
     * @param <T>  记录类型
     * @return 统一分页响应
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        if (page == null) {
            return new PageResult<>(Collections.emptyList(), 0L, 1L, 10L);
        }
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 构造空分页。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @param <T>      记录类型
     * @return 空分页响应
     */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize);
    }
}