package git.ggwzz.tag.service;

import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.mapper.EnterpriseTagRelMapper;
import git.ggwzz.tag.mapper.TagMapper;
import git.ggwzz.tag.service.impl.TagServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link TagServiceImpl} 请求参数校验单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagMapper tagMapper;

    @Mock
    private EnterpriseTagRelMapper enterpriseTagRelMapper;

    @InjectMocks
    private TagServiceImpl tagService;

    @Test
    void listShouldRejectNullQuery() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagService.list(null));

        assertBadRequest(ex);
        verifyNoInteractions(tagMapper, enterpriseTagRelMapper);
    }

    @Test
    void createShouldRejectNullRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagService.create(null));

        assertBadRequest(ex);
        verifyNoInteractions(tagMapper, enterpriseTagRelMapper);
    }

    @Test
    void updateShouldRejectNullRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagService.update(1L, null));

        assertBadRequest(ex);
        verifyNoInteractions(tagMapper, enterpriseTagRelMapper);
    }

    private void assertBadRequest(BusinessException ex) {
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("请求参数不能为空", ex.getMessage());
    }
}
