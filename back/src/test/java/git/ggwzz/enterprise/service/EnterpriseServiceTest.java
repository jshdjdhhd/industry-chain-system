package git.ggwzz.enterprise.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import git.ggwzz.enterprise.dto.EnterpriseQueryRequest;
import git.ggwzz.enterprise.entity.Enterprise;
import git.ggwzz.enterprise.entity.EnterpriseTagRel;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.enterprise.mapper.EnterpriseOptionMapper;
import git.ggwzz.enterprise.mapper.EnterpriseTagRelMapper;
import git.ggwzz.enterprise.service.impl.EnterpriseServiceImpl;
import git.ggwzz.tag.entity.Tag;
import git.ggwzz.tag.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link EnterpriseServiceImpl} 请求参数校验单元测试。
 */
@ExtendWith(MockitoExtension.class)
class EnterpriseServiceTest {

    @Mock
    private EnterpriseMapper enterpriseMapper;

    @Mock
    private EnterpriseOptionMapper enterpriseOptionMapper;

    @Mock
    private EnterpriseTagRelMapper enterpriseTagRelMapper;

    @Mock
    private EnterpriseNodeRelMapper enterpriseNodeRelMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private ChainNodeMapper chainNodeMapper;

    @InjectMocks
    private EnterpriseServiceImpl enterpriseService;

    @Test
    void listShouldRejectNullQuery() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseService.list(null));

        assertBadRequest(ex, "请求参数不能为空");
        verifyNoMapperInteractions();
    }

    @Test
    void createShouldRejectNullRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseService.create(null));

        assertBadRequest(ex, "请求参数不能为空");
        verifyNoMapperInteractions();
    }

    @Test
    void updateShouldRejectNullRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseService.update(1L, null));

        assertBadRequest(ex, "请求参数不能为空");
        verifyNoMapperInteractions();
    }

    @Test
    void batchOutboundShouldRejectNullEnterpriseIds() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseService.batchOutbound(null));

        assertBadRequest(ex, "企业ID列表不能为空");
        verifyNoMapperInteractions();
    }

    @Test
    void outboundShouldMoveActiveEnterpriseToOutboundReviewPool() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(1L);
        enterprise.setStatus("ACTIVE");
        when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

        enterpriseService.outbound(1L);

        assertEquals("OUTBOUND", enterprise.getStatus());
        verify(enterpriseMapper).updateById(enterprise);
    }

    @Test
    void inboundShouldAllowOutboundEnterpriseToReenterActivePool() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(1L);
        enterprise.setStatus("OUTBOUND");
        when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

        enterpriseService.inbound(1L);

        assertEquals("ACTIVE", enterprise.getStatus());
        verify(enterpriseMapper).updateById(enterprise);
    }

    @Test
    void batchInboundShouldProcessUnregisteredAndOutboundEnterprises() {
        Enterprise unregistered = new Enterprise();
        unregistered.setId(1L);
        unregistered.setStatus("UNREGISTERED");
        Enterprise outbound = new Enterprise();
        outbound.setId(2L);
        outbound.setStatus("OUTBOUND");
        when(enterpriseMapper.selectById(1L)).thenReturn(unregistered);
        when(enterpriseMapper.selectById(2L)).thenReturn(outbound);

        int successCount = enterpriseService.batchInbound(java.util.List.of(1L, 2L));

        assertEquals(2, successCount);
        assertEquals("ACTIVE", unregistered.getStatus());
        assertEquals("ACTIVE", outbound.getStatus());
        verify(enterpriseMapper).updateById(unregistered);
        verify(enterpriseMapper).updateById(outbound);
    }

    @Test
    void listShouldBatchLoadTagsInsteadOfPerEnterpriseQueries() {
        when(enterpriseMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<Enterprise> page = invocation.getArgument(0);
            Enterprise e1 = new Enterprise();
            e1.setId(1L);
            e1.setStatus("ACTIVE");
            Enterprise e2 = new Enterprise();
            e2.setId(2L);
            e2.setStatus("ACTIVE");
            page.setRecords(java.util.List.of(e1, e2));
            page.setTotal(2);
            return page;
        });
        EnterpriseTagRel rel1 = new EnterpriseTagRel();
        rel1.setEnterpriseId(1L);
        rel1.setTagId(10L);
        EnterpriseTagRel rel2 = new EnterpriseTagRel();
        rel2.setEnterpriseId(2L);
        rel2.setTagId(20L);
        when(enterpriseTagRelMapper.selectList(any()))
                .thenReturn(java.util.List.of(rel1, rel2));
        Tag tag1 = new Tag();
        tag1.setId(10L);
        Tag tag2 = new Tag();
        tag2.setId(20L);
        when(tagMapper.selectBatchIds(any()))
                .thenReturn(java.util.List.of(tag1, tag2));

        enterpriseService.list(new EnterpriseQueryRequest());

        verify(tagMapper, times(1)).selectBatchIds(any());
        verify(enterpriseTagRelMapper, times(1)).selectList(any());
    }

    private void assertBadRequest(BusinessException ex, String message) {
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals(message, ex.getMessage());
    }

    private void verifyNoMapperInteractions() {
        verifyNoInteractions(
                enterpriseMapper,
                enterpriseOptionMapper,
                enterpriseTagRelMapper,
                enterpriseNodeRelMapper,
                tagMapper,
                chainNodeMapper);
    }
}
