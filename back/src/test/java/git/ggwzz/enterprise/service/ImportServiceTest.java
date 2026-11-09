package git.ggwzz.enterprise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.enterprise.mapper.ImportRecordMapper;
import git.ggwzz.enterprise.service.impl.ImportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link ImportServiceImpl} 参数校验单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private EnterpriseMapper enterpriseMapper;

    @Mock
    private ImportRecordMapper importRecordMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ImportServiceImpl importService;

    @Test
    void importEnterprisesShouldRejectNullFile() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> importService.importEnterprises(null, "admin"));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("上传文件不能为空", ex.getMessage());
        verifyNoInteractions(enterpriseMapper, importRecordMapper, objectMapper);
    }
}
