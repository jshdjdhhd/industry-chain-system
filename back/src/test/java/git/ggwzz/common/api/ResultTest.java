package git.ggwzz.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Result} 与 {@link PageResult} 单元测试。
 */
class ResultTest {

    @Test
    void successWithoutDataShouldHaveCode200() {
        Result<Void> result = Result.success();
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void successWithDataShouldCarryPayload() {
        Result<String> result = Result.success("ok");
        assertEquals(200, result.getCode());
        assertEquals("ok", result.getData());
    }

    @Test
    void failureByResultCodeShouldUseCodeAndMessage() {
        Result<Void> result = Result.failure(ResultCode.NOT_FOUND);
        assertEquals(404, result.getCode());
        assertEquals(ResultCode.NOT_FOUND.getMessage(), result.getMessage());
    }

    @Test
    void failureWithCustomCodeAndMessageShouldOverride() {
        Result<Void> result = Result.failure(400, "用户名不能为空");
        assertEquals(400, result.getCode());
        assertEquals("用户名不能为空", result.getMessage());
    }

    @Test
    void pageResultOfShouldMapIPageFields() {
        Page<String> page = new Page<>(2, 5);
        page.setTotal(13);
        page.setRecords(List.of("a", "b"));
        PageResult<String> pr = PageResult.of(page);
        assertEquals(2, pr.getRecords().size());
        assertEquals(13, pr.getTotal());
        assertEquals(2, pr.getPageNum());
        assertEquals(5, pr.getPageSize());
    }

    @Test
    void pageResultOfNullShouldReturnSafeEmpty() {
        IPage<String> page = null;
        PageResult<String> pr = PageResult.of(page);
        assertNotNull(pr.getRecords());
        assertTrue(pr.getRecords().isEmpty());
        assertEquals(0, pr.getTotal());
    }

    @Test
    void pageResultEmptyShouldHaveZeroTotal() {
        PageResult<String> pr = PageResult.empty(1, 10);
        assertEquals(0, pr.getTotal());
        assertTrue(pr.getRecords().isEmpty());
    }
}