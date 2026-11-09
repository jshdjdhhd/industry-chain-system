package git.ggwzz.chain.service;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.service.impl.ChainNodeQueryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChainNodeQueryServiceImplTest {

    @Mock
    private ChainNodeMapper chainNodeMapper;

    @Mock
    private ChainMapper chainMapper;

    @InjectMocks
    private ChainNodeQueryServiceImpl chainNodeQueryService;

    @Test
    void listByChainId_shouldMapAllFieldsAndKeepMapperOrder() {
        ChainNode first = node(102L, 1L, "Battery Manufacturing", "battery,power battery",
                "Battery manufacturing node", "/101/102/", 2);
        ChainNode second = node(103L, 1L, "Electric Drive", "motor,controller",
                "Electric drive node", "/101/103/", 2);
        ChainNode parent = node(101L, 1L, "Midstream", "midstream",
                "Midstream node", "/101/", 1);
        Chain chain = new Chain();
        chain.setId(1L);
        chain.setChainName("New Energy Vehicle Chain");
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(List.of(first, second));
        when(chainNodeMapper.selectById(102L)).thenReturn(first);
        when(chainNodeMapper.selectById(103L)).thenReturn(second);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(parent, first));
        when(chainNodeMapper.selectBatchIds(List.of(101L, 103L))).thenReturn(List.of(parent, second));

        List<ChainNodeCandidateDTO> result = chainNodeQueryService.listByChainId(1L);

        assertEquals(2, result.size());
        assertEquals(102L, result.get(0).getNodeId());
        assertEquals(1L, result.get(0).getChainId());
        assertEquals("Battery Manufacturing", result.get(0).getNodeName());
        assertEquals("battery,power battery", result.get(0).getKeywords());
        assertEquals(2, result.get(0).getLevel());
        assertEquals("New Energy Vehicle Chain / Midstream / Battery Manufacturing",
                result.get(0).getNodePath());
        assertEquals(103L, result.get(1).getNodeId());
        assertEquals("Electric Drive", result.get(1).getNodeName());
        assertEquals(2, result.get(1).getLevel());
        assertEquals("New Energy Vehicle Chain / Midstream / Electric Drive",
                result.get(1).getNodePath());
        verify(chainNodeMapper).selectByChainId(1L);
        verify(chainMapper, times(2)).selectById(1L);
    }

    @Test
    void listByChainId_shouldReturnNonNullEmptyListWhenMapperReturnsEmpty() {
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(Collections.emptyList());

        List<ChainNodeCandidateDTO> result = chainNodeQueryService.listByChainId(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(chainNodeMapper).selectByChainId(1L);
        verifyNoInteractions(chainMapper);
    }

    @Test
    void buildNodePath_shouldJoinChainAndNodesInPathOrder() {
        ChainNode current = node(102L, 1L, "Battery Manufacturing", "battery,power battery",
                "Battery manufacturing node", "/101/102/", 2);
        ChainNode parent = node(101L, 1L, "Midstream", "midstream", "Midstream node", "/101/", 1);
        Chain chain = new Chain();
        chain.setId(1L);
        chain.setChainName("New Energy Vehicle Chain");
        when(chainNodeMapper.selectById(102L)).thenReturn(current);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(current, parent));

        String result = chainNodeQueryService.buildNodePath(102L);

        assertEquals("New Energy Vehicle Chain / Midstream / Battery Manufacturing", result);
        verify(chainNodeMapper).selectById(102L);
        verify(chainMapper).selectById(1L);
        verify(chainNodeMapper).selectBatchIds(List.of(101L, 102L));
    }

    @Test
    void buildNodePath_shouldReturnNullWithoutMapperAccessWhenNodeIdIsNull() {
        String result = chainNodeQueryService.buildNodePath(null);

        assertNull(result);
        verifyNoInteractions(chainNodeMapper, chainMapper);
    }

    @Test
    void buildNodePath_shouldReturnNullWhenNodeDoesNotExist() {
        when(chainNodeMapper.selectById(999L)).thenReturn(null);

        String result = chainNodeQueryService.buildNodePath(999L);

        assertNull(result);
        verify(chainNodeMapper).selectById(999L);
        verify(chainNodeMapper, never()).selectBatchIds(anyCollection());
        verifyNoInteractions(chainMapper);
    }

    @Test
    void buildNodePath_shouldFallbackToCurrentNodeWhenNodePathIsBlank() {
        ChainNode current = node(102L, 1L, "Battery Manufacturing", "battery",
                "Battery manufacturing node", " ", 1);
        Chain chain = new Chain();
        chain.setId(1L);
        chain.setChainName("New Energy Vehicle Chain");
        when(chainNodeMapper.selectById(102L)).thenReturn(current);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectBatchIds(List.of(102L))).thenReturn(List.of(current));

        String result = chainNodeQueryService.buildNodePath(102L);

        assertEquals("New Energy Vehicle Chain / Battery Manufacturing", result);
    }

    @Test
    void buildNodePath_shouldSkipUnparseablePathSegments() {
        ChainNode current = node(102L, 1L, "Battery Manufacturing", "battery",
                "Battery manufacturing node", "/101/not-a-number/102/", 2);
        ChainNode parent = node(101L, 1L, "Midstream", "midstream", "Midstream node", "/101/", 1);
        Chain chain = new Chain();
        chain.setId(1L);
        chain.setChainName("New Energy Vehicle Chain");
        when(chainNodeMapper.selectById(102L)).thenReturn(current);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(parent, current));

        String result = chainNodeQueryService.buildNodePath(102L);

        assertEquals("New Energy Vehicle Chain / Midstream / Battery Manufacturing", result);
    }

    @Test
    void buildNodePath_shouldReturnNodePathWhenChainDoesNotExist() {
        ChainNode current = node(102L, 1L, "Battery Manufacturing", "battery",
                "Battery manufacturing node", "/101/102/", 2);
        ChainNode parent = node(101L, 1L, "Midstream", "midstream", "Midstream node", "/101/", 1);
        when(chainNodeMapper.selectById(102L)).thenReturn(current);
        when(chainMapper.selectById(1L)).thenReturn(null);
        when(chainNodeMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(parent, current));

        String result = chainNodeQueryService.buildNodePath(102L);

        assertEquals("Midstream / Battery Manufacturing", result);
    }

    @Test
    void buildNodePath_shouldSkipMissingNodesInPath() {
        ChainNode current = node(102L, 1L, "Battery Manufacturing", "battery",
                "Battery manufacturing node", "/101/102/", 2);
        Chain chain = new Chain();
        chain.setId(1L);
        chain.setChainName("New Energy Vehicle Chain");
        when(chainNodeMapper.selectById(102L)).thenReturn(current);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(current));

        String result = chainNodeQueryService.buildNodePath(102L);

        assertEquals("New Energy Vehicle Chain / Battery Manufacturing", result);
    }

    @Test
    void buildNodePath_shouldPropagateMapperException() {
        IllegalStateException databaseError = new IllegalStateException("database unavailable");
        when(chainNodeMapper.selectById(102L)).thenThrow(databaseError);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> chainNodeQueryService.buildNodePath(102L));

        assertSame(databaseError, thrown);
        verifyNoInteractions(chainMapper);
    }

    private ChainNode node(Long id,
                           Long chainId,
                           String name,
                           String keywords,
                           String description,
                           String nodePath,
                           Integer level) {
        ChainNode node = new ChainNode();
        node.setId(id);
        node.setChainId(chainId);
        node.setNodeName(name);
        node.setKeywords(keywords);
        node.setDescription(description);
        node.setNodePath(nodePath);
        node.setNodeLevel(level);
        return node;
    }
}
