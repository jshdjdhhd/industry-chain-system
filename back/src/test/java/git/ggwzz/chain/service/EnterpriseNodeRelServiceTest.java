package git.ggwzz.chain.service;

import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.entity.EnterpriseNodeRel;
import git.ggwzz.chain.enums.BatchLinkEnterpriseStatus;
import git.ggwzz.chain.enums.LinkSource;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.chain.service.impl.EnterpriseNodeRelServiceImpl;
import git.ggwzz.chain.vo.BatchLinkEnterpriseVO;
import git.ggwzz.chain.vo.NodeEnterpriseVO;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link EnterpriseNodeRelServiceImpl} 单元测试，Mockito 隔离 Mapper。
 *
 * <p>覆盖正常挂链、重复挂链抛 409、正常解绑、解绑不存在关系抛 404、
 * 以及 isLinked 查询。</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(
        strictness = org.mockito.quality.Strictness.LENIENT)
class EnterpriseNodeRelServiceTest {

    @Mock
    private EnterpriseNodeRelMapper enterpriseNodeRelMapper;

    @Mock
    private ChainNodeMapper chainNodeMapper;

    private EnterpriseNodeRelServiceImpl enterpriseNodeRelService;

    @BeforeEach
    void setUp() throws Exception {
        enterpriseNodeRelService = new EnterpriseNodeRelServiceImpl(chainNodeMapper);
        // 将 mock mapper 注入到 ServiceImpl.baseMapper 字段
        Field baseMapperField = enterpriseNodeRelService.getClass()
                .getSuperclass().getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(enterpriseNodeRelService, enterpriseNodeRelMapper);
        when(chainNodeMapper.selectById(anyLong())).thenReturn(validNode());
        when(enterpriseNodeRelMapper.selectEnterpriseStatusById(anyLong()))
                .thenReturn("ACTIVE");
    }

    // ======================== linkByAi ========================

    @Test
    void linkByAi_shouldSaveRelation() {
        // 模拟不存在重复关系
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);

        enterpriseNodeRelService.linkByAi(1L, 100L, new BigDecimal("0.9500"),
                "AI推荐理由", 1L);

        ArgumentCaptor<EnterpriseNodeRel> captor =
                ArgumentCaptor.forClass(EnterpriseNodeRel.class);
        verify(enterpriseNodeRelMapper).insert(captor.capture());

        EnterpriseNodeRel saved = captor.getValue();
        assertEquals(1L, saved.getEnterpriseId());
        assertEquals(100L, saved.getNodeId());
        assertEquals(LinkSource.AI.name(), saved.getSource());
        assertEquals(0, new BigDecimal("0.9500").compareTo(saved.getConfidence()));
        assertEquals("AI推荐理由", saved.getReason());
        assertEquals(1L, saved.getConfirmedBy());
    }

    @Test
    void linkByAi_shouldRestoreLogicallyDeletedRelation() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);
        when(enterpriseNodeRelMapper.restoreDeletedRelation(any(EnterpriseNodeRel.class)))
                .thenReturn(1);

        enterpriseNodeRelService.linkByAi(1L, 100L, new BigDecimal("0.9500"),
                "AI推荐理由", 9L);

        ArgumentCaptor<EnterpriseNodeRel> captor =
                ArgumentCaptor.forClass(EnterpriseNodeRel.class);
        verify(enterpriseNodeRelMapper).restoreDeletedRelation(captor.capture());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));

        EnterpriseNodeRel restored = captor.getValue();
        assertEquals(1L, restored.getEnterpriseId());
        assertEquals(100L, restored.getNodeId());
        assertEquals(LinkSource.AI.name(), restored.getSource());
        assertEquals(0, new BigDecimal("0.9500").compareTo(restored.getConfidence()));
        assertEquals("AI推荐理由", restored.getReason());
        assertEquals(9L, restored.getConfirmedBy());
        assertEquals(0, restored.getDeleted());
    }

    @Test
    void linkByAi_shouldThrowOnDuplicate() {
        // 模拟已存在关系
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkByAi(1L, 100L, new BigDecimal("0.9500"),
                        "AI推荐理由", 1L));

        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));
    }

    @Test
    void linkByAi_shouldThrowWhenNodeDeleted() {
        ChainNode deletedNode = validNode();
        deletedNode.setDeleted(1);
        when(chainNodeMapper.selectById(100L)).thenReturn(deletedNode);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkByAi(1L, 100L, new BigDecimal("0.9500"),
                        "AI推荐理由", 1L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("节点不存在或已删除", ex.getMessage());
        verify(enterpriseNodeRelMapper, never()).selectCount(any());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));
    }

    @Test
    void linkByAi_shouldThrowWhenEnterpriseNotFound() {
        when(enterpriseNodeRelMapper.selectEnterpriseStatusById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkByAi(
                        1L, 100L, new BigDecimal("0.9500"),
                        "AI推荐理由", 1L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("企业不存在", ex.getMessage());
        verify(enterpriseNodeRelMapper, never()).selectCount(any());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));
    }

    // ======================== linkManually ========================

    @Test
    void linkManually_shouldSaveRelation() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);

        enterpriseNodeRelService.linkManually(2L, 200L,
                "人工确认理由", 1L);

        ArgumentCaptor<EnterpriseNodeRel> captor =
                ArgumentCaptor.forClass(EnterpriseNodeRel.class);
        verify(enterpriseNodeRelMapper).insert(captor.capture());

        EnterpriseNodeRel saved = captor.getValue();
        assertEquals(2L, saved.getEnterpriseId());
        assertEquals(200L, saved.getNodeId());
        assertEquals(LinkSource.MANUAL.name(), saved.getSource());
        assertNull(saved.getConfidence());
        assertEquals("人工确认理由", saved.getReason());
    }

    @Test
    void linkManually_shouldThrowOnDuplicate() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkManually(2L, 200L,
                        "人工确认理由", 1L));

        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
    }

    @Test
    void linkManually_shouldThrowWhenNodeNotFound() {
        when(chainNodeMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkManually(2L, 999L,
                        "人工确认理由", 1L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("节点不存在或已删除", ex.getMessage());
        verify(enterpriseNodeRelMapper, never()).selectCount(any());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));
    }

    @Test
    void linkManually_shouldThrowWhenEnterpriseNotFound() {
        when(enterpriseNodeRelMapper.selectEnterpriseStatusById(2L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkManually(
                        2L, 200L, "人工确认理由", 1L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("企业不存在", ex.getMessage());
        verify(enterpriseNodeRelMapper, never()).selectCount(any());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));
    }

    @Test
    void linkManually_shouldThrowWhenEnterpriseNotActive() {
        when(enterpriseNodeRelMapper.selectEnterpriseStatusById(2L)).thenReturn("UNREGISTERED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkManually(
                        2L, 200L, "人工确认理由", 1L));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("企业状态不可挂接", ex.getMessage());
        verify(enterpriseNodeRelMapper, never()).selectCount(any());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));
    }

    // ======================== linkManuallyBatch ========================

    @Test
    void linkManuallyBatch_shouldRejectNullEnterpriseIds() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkManuallyBatch(
                        200L, null, 1L));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("企业ID列表不能为空", ex.getMessage());
        verifyNoInteractions(chainNodeMapper);
        verify(enterpriseNodeRelMapper, never()).selectCount(any());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));
    }

    @Test
    void linkManuallyBatch_shouldLinkAllEnterprises() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);

        BatchLinkEnterpriseVO result = enterpriseNodeRelService.linkManuallyBatch(
                200L, List.of(1L, 2L), 1L);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getLinkedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(BatchLinkEnterpriseStatus.LINKED, result.getItems().get(0).getStatus());
        assertEquals(BatchLinkEnterpriseStatus.LINKED, result.getItems().get(1).getStatus());
        verify(enterpriseNodeRelMapper, times(2)).insert(any(EnterpriseNodeRel.class));
    }

    @Test
    void linkManuallyBatch_shouldSkipAlreadyLinkedEnterprise() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(1L, 0L);

        BatchLinkEnterpriseVO result = enterpriseNodeRelService.linkManuallyBatch(
                200L, List.of(1L, 2L), 1L);

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getLinkedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(BatchLinkEnterpriseStatus.ALREADY_LINKED, result.getItems().get(0).getStatus());
        assertEquals(BatchLinkEnterpriseStatus.LINKED, result.getItems().get(1).getStatus());
        verify(enterpriseNodeRelMapper, times(1)).insert(any(EnterpriseNodeRel.class));
    }

    @Test
    void linkManuallyBatch_shouldSkipDuplicateEnterpriseInRequest() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L, 0L);

        BatchLinkEnterpriseVO result = enterpriseNodeRelService.linkManuallyBatch(
                200L, List.of(1L, 1L, 2L), 1L);

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getLinkedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(BatchLinkEnterpriseStatus.LINKED, result.getItems().get(0).getStatus());
        assertEquals(BatchLinkEnterpriseStatus.DUPLICATE_IN_REQUEST, result.getItems().get(1).getStatus());
        assertEquals(BatchLinkEnterpriseStatus.LINKED, result.getItems().get(2).getStatus());
        verify(enterpriseNodeRelMapper, times(2)).insert(any(EnterpriseNodeRel.class));
        verify(enterpriseNodeRelMapper, times(2)).selectCount(any());
    }

    @Test
    void linkManuallyBatch_shouldRestoreLogicallyDeletedRelation() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);
        when(enterpriseNodeRelMapper.restoreDeletedRelation(any(EnterpriseNodeRel.class)))
                .thenReturn(1);

        BatchLinkEnterpriseVO result = enterpriseNodeRelService.linkManuallyBatch(
                200L, List.of(1L), 9L);

        assertEquals(1, result.getLinkedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(BatchLinkEnterpriseStatus.LINKED, result.getItems().get(0).getStatus());

        ArgumentCaptor<EnterpriseNodeRel> captor =
                ArgumentCaptor.forClass(EnterpriseNodeRel.class);
        verify(enterpriseNodeRelMapper).restoreDeletedRelation(captor.capture());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));

        EnterpriseNodeRel restored = captor.getValue();
        assertEquals(1L, restored.getEnterpriseId());
        assertEquals(200L, restored.getNodeId());
        assertEquals(LinkSource.MANUAL.name(), restored.getSource());
        assertEquals("人工挂链", restored.getReason());
        assertEquals(9L, restored.getConfirmedBy());
        assertEquals(0, restored.getDeleted());
    }

    @Test
    void linkManuallyBatch_shouldRecordFailureAndContinue() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);
        when(enterpriseNodeRelMapper.selectEnterpriseStatusById(2L)).thenReturn(null);

        BatchLinkEnterpriseVO result = enterpriseNodeRelService.linkManuallyBatch(
                200L, List.of(1L, 2L, 3L), 1L);

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getLinkedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(BatchLinkEnterpriseStatus.LINKED, result.getItems().get(0).getStatus());
        assertEquals(BatchLinkEnterpriseStatus.FAILED, result.getItems().get(1).getStatus());
        assertEquals("挂接失败：企业不存在", result.getItems().get(1).getMessage());
        assertEquals(BatchLinkEnterpriseStatus.LINKED, result.getItems().get(2).getStatus());
        verify(enterpriseNodeRelMapper, times(2)).insert(any(EnterpriseNodeRel.class));
    }

    @Test
    void linkManuallyBatch_shouldThrowWhenNodeNotFound() {
        when(chainNodeMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkManuallyBatch(
                        999L, List.of(1L, 2L), 1L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("节点不存在或已删除", ex.getMessage());
        verify(enterpriseNodeRelMapper, never()).selectCount(any());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));
    }

    @Test
    void linkManuallyBatch_shouldValidateNodeOnce() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);

        enterpriseNodeRelService.linkManuallyBatch(200L, List.of(1L, 2L), 1L);

        verify(chainNodeMapper, times(1)).selectById(200L);
    }

    // ======================== listByNodeId ========================

    @Test
    void listByNodeId_shouldThrowWhenNodeNotFound() {
        when(chainNodeMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.listByNodeId(999L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("节点不存在或已删除", ex.getMessage());
        verify(enterpriseNodeRelMapper, never()).selectNodeEnterpriseVosByNodeId(anyLong());
    }

    @Test
    void listByNodeId_shouldReturnEmptyListForValidNode() {
        when(enterpriseNodeRelMapper.selectNodeEnterpriseVosByNodeId(100L))
                .thenReturn(List.of());

        List<NodeEnterpriseVO> result = enterpriseNodeRelService.listByNodeId(100L);

        assertTrue(result.isEmpty());
        verify(chainNodeMapper).selectById(100L);
        verify(enterpriseNodeRelMapper).selectNodeEnterpriseVosByNodeId(100L);
    }

    @Test
    void listByNodeId_shouldReturnNodeEnterpriseVOs() {
        NodeEnterpriseVO enterprise = new NodeEnterpriseVO();
        enterprise.setEnterpriseId(1L);
        enterprise.setEnterpriseName("合肥测试企业");
        enterprise.setSource(LinkSource.MANUAL.name());
        when(enterpriseNodeRelMapper.selectNodeEnterpriseVosByNodeId(100L))
                .thenReturn(List.of(enterprise));

        List<NodeEnterpriseVO> result = enterpriseNodeRelService.listByNodeId(100L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getEnterpriseId());
        assertEquals("合肥测试企业", result.get(0).getEnterpriseName());
        assertEquals(LinkSource.MANUAL.name(), result.get(0).getSource());
        verify(chainNodeMapper).selectById(100L);
        verify(enterpriseNodeRelMapper).selectNodeEnterpriseVosByNodeId(100L);
    }

    // ======================== unlink ========================

    @Test
    void unlink_shouldDeleteRelation() {
        EnterpriseNodeRel existing = new EnterpriseNodeRel();
        existing.setId(10L);
        existing.setEnterpriseId(1L);
        existing.setNodeId(100L);

        doReturn(existing).when(enterpriseNodeRelMapper).selectOne(any());

        enterpriseNodeRelService.unlink(100L, 1L, 1L);

        verify(enterpriseNodeRelMapper).deleteById(10L);
    }

    @Test
    void unlink_shouldThrowOnNotFound() {
        doReturn(null).when(enterpriseNodeRelMapper).selectOne(any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.unlink(100L, 1L, 1L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        verify(enterpriseNodeRelMapper, never()).deleteById(anyLong());
    }

    @Test
    void unlink_shouldThrowWhenNodeNotFound() {
        when(chainNodeMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.unlink(999L, 1L, 1L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("节点不存在或已删除", ex.getMessage());
        verify(enterpriseNodeRelMapper, never()).selectOne(any());
        verify(enterpriseNodeRelMapper, never()).deleteById(anyLong());
    }

    @Test
    void linkByAi_shouldThrowWhenNodeIdIsNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> enterpriseNodeRelService.linkByAi(
                        1L, null, new BigDecimal("0.9500"), "AI推荐理由", 1L));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("节点 ID 不能为空", ex.getMessage());
        verifyNoInteractions(chainNodeMapper);
        verify(enterpriseNodeRelMapper, never()).selectCount(any());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));
    }

    // ======================== isLinked ========================

    @Test
    void isLinked_shouldReturnTrueWhenExists() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(1L);

        assertTrue(enterpriseNodeRelService.isLinked(1L, 100L));
    }

    @Test
    void isLinked_shouldReturnFalseWhenNotExists() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);

        assertFalse(enterpriseNodeRelService.isLinked(1L, 100L));
    }

    @Test
    void listLinkedNodeIdsByEnterpriseAndChain_shouldReturnDistinctNodeIds() {
        when(enterpriseNodeRelMapper.selectLinkedNodeIdsByEnterpriseAndChain(1L, 10L))
                .thenReturn(List.of(100L, 101L, 100L));

        Set<Long> result = enterpriseNodeRelService.listLinkedNodeIdsByEnterpriseAndChain(1L, 10L);

        assertEquals(Set.of(100L, 101L), result);
    }

    private ChainNode validNode() {
        ChainNode node = new ChainNode();
        node.setId(100L);
        node.setDeleted(0);
        return node;
    }
}
