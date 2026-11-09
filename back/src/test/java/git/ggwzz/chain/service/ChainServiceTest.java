package git.ggwzz.chain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import git.ggwzz.chain.dto.CreateChainRequest;
import git.ggwzz.chain.dto.CreateNodeRequest;
import git.ggwzz.chain.dto.MoveNodeRequest;
import git.ggwzz.chain.dto.SortNodeOrderRequest;
import git.ggwzz.chain.dto.SortNodesRequest;
import git.ggwzz.chain.dto.UpdateChainRequest;
import git.ggwzz.chain.dto.UpdateNodeRequest;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.enums.ChainStageType;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.chain.mapper.projection.ChainSummaryCountRow;
import git.ggwzz.chain.mapper.projection.EnterpriseCountSummary;
import git.ggwzz.chain.mapper.projection.NodeEnterpriseCountRow;
import git.ggwzz.chain.mapper.projection.NodeSubtreeEnterpriseCountRow;
import git.ggwzz.chain.service.impl.ChainServiceImpl;
import git.ggwzz.chain.vo.ChainVO;
import git.ggwzz.chain.vo.EnterpriseCountVO;
import git.ggwzz.chain.vo.NodeVO;
import git.ggwzz.chain.vo.TreeNodeVO;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 产业链管理服务核心逻辑单元测试。
 *
 * <p>覆盖 5 个核心点：树形构建、删除校验、递归统计、节点层级计算、未找到资源校验。
 * 纯 Mockito，mock 三个 Mapper（含 BaseMapper 方法与命名方法），不连真实数据库。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChainServiceTest {

    @Mock
    private ChainMapper chainMapper;

    @Mock
    private ChainNodeMapper chainNodeMapper;

    @Mock
    private EnterpriseNodeRelMapper enterpriseNodeRelMapper;

    @InjectMocks
    private ChainServiceImpl chainService;

    // ==================== 产业链请求参数校验 ====================

    @Test
    void should_throw_bad_request_when_create_chain_request_is_null() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.createChain(null));

        assertEquals(400, exception.getCode());
        assertEquals("请求参数不能为空", exception.getMessage());
        verify(chainMapper, never()).insert(any(Chain.class));
    }

    @Test
    void should_throw_bad_request_when_update_chain_request_is_null() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.updateChain(1L, null));

        assertEquals(400, exception.getCode());
        assertEquals("请求参数不能为空", exception.getMessage());
        verify(chainMapper, never()).selectById(anyLong());
    }

    @Test
    void should_throw_bad_request_when_create_node_request_is_null() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.createNode(null));

        assertEquals(400, exception.getCode());
        assertEquals("请求参数不能为空", exception.getMessage());
        verify(chainMapper, never()).selectById(anyLong());
    }

    @Test
    void should_throw_bad_request_when_update_node_request_is_null() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.updateNode(1L, null));

        assertEquals(400, exception.getCode());
        assertEquals("请求参数不能为空", exception.getMessage());
        verify(chainNodeMapper, never()).selectById(anyLong());
    }

    @Test
    void should_throw_bad_request_when_move_node_request_is_null() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.moveNode(1L, null));

        assertEquals(400, exception.getCode());
        assertEquals("请求参数不能为空", exception.getMessage());
        verify(chainNodeMapper, never()).selectById(anyLong());
    }

    @Test
    void should_throw_bad_request_when_sort_nodes_request_is_null() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.sortNodes(null));

        assertEquals(400, exception.getCode());
        assertEquals("请求参数不能为空", exception.getMessage());
        verify(chainNodeMapper, never()).update(any(), any());
    }

    // ==================== 产业链汇总统计 ====================

    @Test
    void should_fill_chain_summary_counts_with_one_batch_query() {
        Chain first = chain(1L, "新能源汽车");
        Chain second = chain(2L, "集成电路");
        when(chainMapper.selectPage(
                any(Page.class),
                any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Chain> page = invocation.getArgument(0);
                    page.setRecords(List.of(first, second));
                    page.setTotal(2L);
                    return page;
                });
        when(chainMapper.selectSummaryCountsByChainIds(List.of(1L, 2L)))
                .thenReturn(List.of(summaryCountRow(1L, 4L, 3L)));

        PageResult<ChainVO> result = chainService.listChains(1, 10);

        assertEquals(2, result.getRecords().size());
        assertEquals(2L, result.getTotal());
        assertEquals(1L, result.getPageNum());
        assertEquals(10L, result.getPageSize());
        assertEquals(4L, result.getRecords().get(0).getNodeCount());
        assertEquals(3L, result.getRecords().get(0).getEnterpriseCount());
        assertEquals(0L, result.getRecords().get(1).getNodeCount());
        assertEquals(0L, result.getRecords().get(1).getEnterpriseCount());
        verify(chainMapper, times(1))
                .selectSummaryCountsByChainIds(List.of(1L, 2L));
    }

    @Test
    void should_skip_summary_query_when_chain_page_is_empty() {
        when(chainMapper.selectPage(
                any(Page.class),
                any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Chain> page = invocation.getArgument(0);
                    page.setRecords(Collections.emptyList());
                    page.setTotal(0L);
                    return page;
                });

        PageResult<ChainVO> result = chainService.listChains(3, 20);

        assertTrue(result.getRecords().isEmpty());
        assertEquals(0L, result.getTotal());
        assertEquals(3L, result.getPageNum());
        assertEquals(20L, result.getPageSize());
        verify(chainMapper, never()).selectSummaryCountsByChainIds(any());
    }

    @Test
    void should_create_three_stage_nodes_when_create_chain() {
        when(chainMapper.insert(any(Chain.class))).thenAnswer(invocation -> {
            Chain chain = invocation.getArgument(0);
            chain.setId(1L);
            return 1;
        });
        long[] nextId = {10L};
        when(chainNodeMapper.insert(any(ChainNode.class)))
                .thenAnswer(invocation -> {
                    ChainNode node = invocation.getArgument(0);
                    node.setId(nextId[0]++);
                    return 1;
                });
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);
        CreateChainRequest request = new CreateChainRequest();
        request.setChainName("新能源汽车");
        request.setDescription("测试产业链");

        ChainVO result = chainService.createChain(request);

        assertEquals(1L, result.getId());
        assertEquals(3L, result.getNodeCount());
        assertEquals(0L, result.getEnterpriseCount());
        ArgumentCaptor<ChainNode> captor =
                ArgumentCaptor.forClass(ChainNode.class);
        verify(chainNodeMapper, times(3)).insert(captor.capture());
        assertEquals(
                List.of(
                        ChainStageType.UPSTREAM,
                        ChainStageType.MIDSTREAM,
                        ChainStageType.DOWNSTREAM),
                captor.getAllValues().stream()
                        .map(ChainNode::getStageType)
                        .toList());
        assertEquals(
                List.of("/10/", "/11/", "/12/"),
                captor.getAllValues().stream()
                        .map(ChainNode::getNodePath)
                        .toList());
        verify(chainMapper, never()).selectSummaryCountsByChainIds(any());
    }

    @Test
    void should_reject_blank_chain_name_in_service() {
        CreateChainRequest request = new CreateChainRequest();
        request.setChainName("   ");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.createChain(request));

        assertEquals(400, ex.getCode());
        assertEquals("产业链名称不能为空", ex.getMessage());
        verify(chainMapper, never()).insert(any(Chain.class));
    }

    @Test
    void should_fail_when_chain_id_is_not_generated() {
        when(chainMapper.insert(any(Chain.class))).thenReturn(1);
        CreateChainRequest request = new CreateChainRequest();
        request.setChainName("测试链");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.createChain(request));

        assertEquals(500, ex.getCode());
        assertEquals("产业链创建失败", ex.getMessage());
        verify(chainNodeMapper, never()).insert(any(ChainNode.class));
    }

    @Test
    void should_fail_when_stage_insert_returns_zero() {
        when(chainMapper.insert(any(Chain.class))).thenAnswer(invocation -> {
            Chain chain = invocation.getArgument(0);
            chain.setId(1L);
            return 1;
        });
        when(chainNodeMapper.insert(any(ChainNode.class))).thenReturn(0);
        CreateChainRequest request = new CreateChainRequest();
        request.setChainName("测试链");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.createChain(request));

        assertEquals("产业链阶段节点初始化失败", ex.getMessage());
    }

    @Test
    void should_return_real_summary_counts_when_get_chain() {
        Chain chain = chain(1L, "新能源汽车");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
                .thenReturn(List.of(summaryCountRow(1L, 8L, 5L)));

        ChainVO result = chainService.getChain(1L);

        assertEquals(8L, result.getNodeCount());
        assertEquals(5L, result.getEnterpriseCount());
        verify(chainMapper, times(1))
                .selectSummaryCountsByChainIds(List.of(1L));
    }

    @Test
    void should_return_real_summary_counts_when_update_chain() {
        Chain chain = chain(1L, "新能源汽车");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
                .thenReturn(List.of(summaryCountRow(1L, 6L, 4L)));
        UpdateChainRequest request = new UpdateChainRequest();
        request.setChainName("新能源汽车产业链");

        ChainVO result = chainService.updateChain(1L, request);

        assertEquals("新能源汽车产业链", result.getChainName());
        assertEquals(6L, result.getNodeCount());
        assertEquals(4L, result.getEnterpriseCount());
        verify(chainMapper).updateById(chain);
        verify(chainMapper, times(1))
                .selectSummaryCountsByChainIds(List.of(1L));
    }

    @Test
    void should_reject_blank_chain_name_on_update() {
        Chain chain = chain(1L, "新能源汽车");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
                .thenReturn(Collections.emptyList());
        UpdateChainRequest request = new UpdateChainRequest();
        request.setChainName("   ");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.updateChain(1L, request));

        assertEquals(400, ex.getCode());
        assertEquals("产业链名称不能为空", ex.getMessage());
        verify(chainMapper, never()).updateById(any(Chain.class));
    }

    // ==================== 1. 树形构建 ====================

    @Test
    void should_build_tree_when_get_chain_tree() {
        // given：产业链 1 下 上游(2,一级,/2/) → 原材料(3,二级,/2/3/)
        Chain chain = new Chain();
        chain.setId(1L);
        chain.setChainName("新能源汽车");

        ChainNode up = new ChainNode();
        up.setId(2L);
        up.setChainId(1L);
        up.setParentId(null);
        up.setNodeName("上游");
        up.setNodeLevel(1);
        up.setNodePath("/2/");
        up.setStageType(ChainStageType.UPSTREAM);
        up.setSortOrder(1);

        ChainNode raw = new ChainNode();
        raw.setId(3L);
        raw.setChainId(1L);
        raw.setParentId(2L);
        raw.setNodeName("原材料");
        raw.setNodeLevel(2);
        raw.setNodePath("/2/3/");
        raw.setSortOrder(7);

        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(Arrays.asList(up, raw));
        when(enterpriseNodeRelMapper.selectDirectCountsByChainId(1L))
                .thenReturn(List.of(
                        countRow(2L, 1L),
                        countRow(3L, 2L)));
        when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
                .thenReturn(List.of(
                        subtreeCountRow(2L, 3L),
                        subtreeCountRow(3L, 2L)));
        when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
                .thenReturn(List.of(summaryCountRow(1L, 2L, 3L)));

        // when
        TreeNodeVO root = chainService.getChainTree(1L);

        // then
        assertNotNull(root);
        assertEquals("新能源汽车", root.getNodeName());
        assertEquals(1, root.getChildren().size(), "一级节点应有 1 个");

        TreeNodeVO upNode = root.getChildren().get(0);
        assertEquals("上游", upNode.getNodeName());
        assertEquals(ChainStageType.UPSTREAM, upNode.getStageType());
        assertEquals(1, upNode.getDirectEnterpriseCount(), "上游直接企业数应为 1");
        assertEquals(3, upNode.getSubtreeEnterpriseCount(), "上游子树企业数应为 3");
        assertEquals(1, upNode.getChildren().size(), "上游下应有原材料");
        assertNull(upNode.getParentId());
        assertEquals(1, upNode.getSortOrder());

        TreeNodeVO rawNode = upNode.getChildren().get(0);
        assertEquals("原材料", rawNode.getNodeName());
        assertEquals(2, rawNode.getDirectEnterpriseCount(), "原材料直接企业数应为 2");
        assertEquals(2, rawNode.getSubtreeEnterpriseCount(), "原材料子树企业数应为 2");
        assertEquals(2L, rawNode.getParentId());
        assertEquals(7, rawNode.getSortOrder());
        assertNotNull(rawNode.getChildren(), "叶子节点 children 不能为 null");
        assertTrue(rawNode.getChildren().isEmpty(), "叶子节点 children 应为空数组");
        assertEquals(3, root.getSubtreeEnterpriseCount());
        verify(enterpriseNodeRelMapper, times(1))
                .selectDirectCountsByChainId(1L);
        verify(enterpriseNodeRelMapper).selectSubtreeCountsByChainId(1L);
        verify(chainMapper).selectSummaryCountsByChainIds(List.of(1L));
    }

    @Test
    void shouldUseDistinctChainCountForVirtualRoot() {
        Chain chain = chain(1L, "新能源汽车");
        ChainNode first = rootNode(100L, "中游");
        ChainNode second = rootNode(103L, "整车制造");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectByChainId(1L))
                .thenReturn(List.of(first, second));
        when(enterpriseNodeRelMapper.selectDirectCountsByChainId(1L))
                .thenReturn(List.of(
                        countRow(100L, 1L),
                        countRow(103L, 1L)));
        when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
                .thenReturn(List.of(
                        subtreeCountRow(100L, 1L),
                        subtreeCountRow(103L, 1L)));
        when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
                .thenReturn(List.of(summaryCountRow(1L, 2L, 1L)));

        TreeNodeVO root = chainService.getChainTree(1L);

        assertEquals(1, root.getSubtreeEnterpriseCount());
        assertEquals(1, root.getChildren().get(0).getSubtreeEnterpriseCount());
        assertEquals(1, root.getChildren().get(1).getSubtreeEnterpriseCount());
    }

    @Test
    void should_query_enterprise_counts_once_for_many_tree_nodes() {
        Chain chain = new Chain();
        chain.setId(1L);
        chain.setChainName("大规模产业链");
        List<ChainNode> nodes = LongStream.rangeClosed(1, 1000)
                .mapToObj(id -> rootNode(id, "节点" + id))
                .toList();
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(nodes);
        when(enterpriseNodeRelMapper.selectDirectCountsByChainId(1L))
                .thenReturn(Collections.emptyList());
        when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
                .thenReturn(Collections.emptyList());
        when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
                .thenReturn(Collections.emptyList());

        TreeNodeVO tree = chainService.getChainTree(1L);

        assertEquals(1000, tree.getChildren().size());
        verify(chainNodeMapper, times(1)).selectByChainId(1L);
        verify(enterpriseNodeRelMapper, times(1))
                .selectDirectCountsByChainId(1L);
        verify(enterpriseNodeRelMapper, times(1))
                .selectSubtreeCountsByChainId(1L);
        verify(chainMapper, times(1))
                .selectSummaryCountsByChainIds(List.of(1L));
        assertEquals(0, tree.getSubtreeEnterpriseCount());
    }

    @Test
    void should_throw_not_found_when_chain_not_found() {
        when(chainMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chainService.getChainTree(99L));

        assertEquals(404, ex.getCode());
    }

    @Test
    void should_map_keywords_and_description_in_tree_nodes() {
        // given：树节点携带 keywords / description，组装 TreeNodeVO 时应一并透出
        Chain chain = new Chain();
        chain.setId(1L);
        chain.setChainName("新能源汽车");

        ChainNode up = new ChainNode();
        up.setId(2L);
        up.setChainId(1L);
        up.setParentId(null);
        up.setNodeName("上游");
        up.setNodeLevel(1);
        up.setNodePath("/2/");
        up.setStageType(ChainStageType.UPSTREAM);
        up.setKeywords("氢气制取,电解水制氢");
        up.setDescription("氢气生产环节");

        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(List.of(up));
        when(enterpriseNodeRelMapper.selectDirectCountsByChainId(1L))
                .thenReturn(Collections.emptyList());
        when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
                .thenReturn(Collections.emptyList());
        when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
                .thenReturn(Collections.emptyList());

        // when
        TreeNodeVO root = chainService.getChainTree(1L);

        // then
        TreeNodeVO upNode = root.getChildren().get(0);
        assertEquals("氢气制取,电解水制氢", upNode.getKeywords());
        assertEquals("氢气生产环节", upNode.getDescription());
    }

    // ==================== 2. 删除校验 ====================

    @Test
    void should_delete_chain_without_loading_node_ids() {
        Chain chain = new Chain();
        chain.setId(1L);
        when(chainMapper.selectById(1L)).thenReturn(chain);

        chainService.deleteChain(1L);

        verify(enterpriseNodeRelMapper).logicalDeleteByChainId(1L);
        verify(chainNodeMapper, never()).selectByChainId(1L);
        verify(chainNodeMapper).delete(any(LambdaQueryWrapper.class));
        verify(chainMapper).deleteById(1L);
    }

    @Test
    void should_throw_when_delete_node_has_children() {
        // given：节点 4 有 2 个子节点
        ChainNode node = new ChainNode();
        node.setId(4L);
        node.setNodeName("上游");
        when(chainNodeMapper.selectById(4L)).thenReturn(node);
        when(chainNodeMapper.countChildren(4L)).thenReturn(2);

        // when
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chainService.deleteNode(4L));

        // then
        assertEquals(400, ex.getCode());
        verify(chainNodeMapper, never()).deleteById(anyLong());
    }

    @Test
    void should_delete_when_node_is_leaf() {
        // given：叶子节点无子节点
        ChainNode node = new ChainNode();
        node.setId(9L);
        node.setNodeName("正极材料");
        when(chainNodeMapper.selectById(9L)).thenReturn(node);
        when(chainNodeMapper.countChildren(9L)).thenReturn(0);

        // when
        chainService.deleteNode(9L);

        // then：先清理挂链关系，再逻辑删除节点
        verify(enterpriseNodeRelMapper, times(1)).delete(any());
        verify(chainNodeMapper, times(1)).deleteById(9L);
    }

    @Test
    void should_throw_not_found_when_delete_nonexistent_node() {
        when(chainNodeMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chainService.deleteNode(99L));

        assertEquals(404, ex.getCode());
    }

    // ==================== 3. 递归统计 ====================

    @Test
    void should_count_all_descendants_when_get_enterprise_count() {
        // given：中游(5, /5/) → 电芯(6) → 正极材料(7)，直接挂链 3 家，含下级共 6 家
        ChainNode mid = new ChainNode();
        mid.setId(5L);
        mid.setChainId(1L);
        mid.setNodeName("中游");
        mid.setNodePath("/5/");

        EnterpriseCountSummary summary = new EnterpriseCountSummary();
        summary.setDirectEnterpriseCount(3L);
        summary.setTotalEnterpriseCount(6L);
        when(chainNodeMapper.selectById(5L)).thenReturn(mid);
        when(enterpriseNodeRelMapper.selectEnterpriseCountSummary(
                1L, 5L, "/5/%")).thenReturn(summary);

        // when
        EnterpriseCountVO vo = chainService.getEnterpriseCount(5L);

        // then
        assertEquals(5L, vo.getNodeId());
        assertEquals(3, vo.getDirectEnterpriseCount(), "直接关联企业数应为 3");
        assertEquals(6, vo.getTotalEnterpriseCount(), "含下级总企业数应为 6");
        verify(enterpriseNodeRelMapper).selectEnterpriseCountSummary(
                1L, 5L, "/5/%");
    }

    @Test
    void should_keep_node_list_database_paged() {
        ChainNode node = rootNode(2L, "上游");
        when(chainNodeMapper.selectPage(
                any(Page.class),
                any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<ChainNode> page = invocation.getArgument(0);
                    page.setRecords(List.of(node));
                    page.setTotal(201L);
                    return page;
                });

        PageResult<NodeVO> result =
                chainService.listNodes(1L, null, 3, 100);

        assertEquals(1, result.getRecords().size());
        assertEquals(201L, result.getTotal());
        assertEquals(3L, result.getPageNum());
        assertEquals(100L, result.getPageSize());
        verify(chainNodeMapper).selectPage(
                any(Page.class),
                any(LambdaQueryWrapper.class));
    }

    // ==================== 4. 节点层级 / node_path 计算 ====================

    @Test
    void should_set_level_when_create_child_node() {
        // given：父节点 level=2、nodePath=/5/、属于产业链 1
        Chain chain = new Chain();
        chain.setId(1L);
        ChainNode parent = new ChainNode();
        parent.setId(5L);
        parent.setChainId(1L);
        parent.setNodeLevel(2);
        parent.setNodePath("/5/");

        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectById(5L)).thenReturn(parent);
        // 模拟 insert 回填自增 id
        when(chainNodeMapper.insert(any(ChainNode.class))).thenAnswer(inv -> {
            ChainNode n = inv.getArgument(0);
            n.setId(6L);
            return 1;
        });

        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(1L);
        request.setParentId(5L);
        request.setNodeName("电芯");
        request.setKeywords("动力电池,电芯,模组");

        // when
        NodeVO vo = chainService.createNode(request);

        // then
        assertEquals(3, vo.getNodeLevel(), "子节点 level 应为父节点 level+1");
        assertEquals("/5/6/", vo.getNodePath(), "node_path 应为父路径 + 自身 id");
        assertEquals("动力电池,电芯,模组", vo.getKeywords(), "节点关键词应随创建请求保存");
        verify(chainNodeMapper, times(1)).insert(any(ChainNode.class));
        verify(chainNodeMapper, times(1)).updateById(any(ChainNode.class));
    }

    @Test
    void should_set_level_1_when_create_root_node() {
        // given：不传 parentId，创建一级节点
        Chain chain = new Chain();
        chain.setId(1L);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.insert(any(ChainNode.class))).thenAnswer(inv -> {
            ChainNode n = inv.getArgument(0);
            n.setId(2L);
            return 1;
        });

        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(1L);
        request.setParentId(null);
        request.setNodeName("上游");

        // when
        NodeVO vo = chainService.createNode(request);

        // then
        assertEquals(1, vo.getNodeLevel(), "一级节点 level 应为 1");
        assertEquals("/2/", vo.getNodePath(), "根节点 node_path 应为 /id/");
        verify(chainNodeMapper, times(1)).insert(any(ChainNode.class));
        verify(chainNodeMapper, never()).selectById(anyLong());
    }

    @Test
    void should_throw_bad_request_when_parent_not_found() {
        // given：父节点不存在
        Chain chain = new Chain();
        chain.setId(1L);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectById(99L)).thenReturn(null);

        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(1L);
        request.setParentId(99L);
        request.setNodeName("测试节点");

        // when
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chainService.createNode(request));

        // then
        assertEquals(400, ex.getCode());
        verify(chainNodeMapper, never()).insert(any(ChainNode.class));
    }

    @Test
    void should_throw_bad_request_when_parent_belongs_to_another_chain() {
        // given：目标产业链 ID 为 2，父节点存在但其 chainId 为 1
        Chain chain = new Chain();
        chain.setId(2L);
        ChainNode parent = new ChainNode();
        parent.setId(5L);
        parent.setChainId(1L);
        parent.setNodeLevel(1);
        parent.setNodePath("/5/");

        when(chainMapper.selectById(2L)).thenReturn(chain);
        when(chainNodeMapper.selectById(5L)).thenReturn(parent);

        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(2L);
        request.setParentId(5L);
        request.setNodeName("跨产业链子节点");

        // when
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chainService.createNode(request));

        // then
        assertEquals(400, ex.getCode());
        assertEquals("父节点不属于当前产业链", ex.getMessage());
        verify(chainNodeMapper, never()).insert(any(ChainNode.class));
        verify(chainNodeMapper, never()).updateById(any(ChainNode.class));
    }

    @Test
    void should_throw_bad_request_when_chain_not_found_on_create_node() {
        // given：产业链不存在
        when(chainMapper.selectById(99L)).thenReturn(null);

        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(99L);
        request.setParentId(null);
        request.setNodeName("测试节点");

        // when
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chainService.createNode(request));

        // then
        assertEquals(400, ex.getCode());
        verify(chainNodeMapper, never()).insert(any(ChainNode.class));
    }

    // ==================== 5. 更新节点 ====================

    @Test
    void should_normalize_keywords_when_create_node() {
        // given：关键词含空格、空项、重复项
        Chain chain = new Chain();
        chain.setId(1L);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.insert(any(ChainNode.class))).thenAnswer(inv -> {
            ChainNode n = inv.getArgument(0);
            n.setId(2L);
            return 1;
        });

        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(1L);
        request.setNodeName("电芯");
        request.setKeywords(" 电芯 , 原材料,, 正极材料, 电芯 ");

        // when
        NodeVO vo = chainService.createNode(request);

        // then：去空格、去空项、去重，逗号分隔
        assertEquals("电芯,原材料,正极材料", vo.getKeywords());
        verify(chainNodeMapper, times(1)).insert(any(ChainNode.class));
    }

    @Test
    void should_set_keywords_null_when_create_node_with_blank_keywords() {
        // given：仅空白字符的关键词应清洗为 null，避免落库空串
        Chain chain = new Chain();
        chain.setId(1L);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.insert(any(ChainNode.class))).thenAnswer(inv -> {
            ChainNode n = inv.getArgument(0);
            n.setId(2L);
            return 1;
        });

        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(1L);
        request.setNodeName("电芯");
        request.setKeywords("   ");

        // when
        NodeVO vo = chainService.createNode(request);

        // then
        assertNull(vo.getKeywords(), "空白关键词应清洗为 null");
    }

    @Test
    void should_map_description_when_create_node() {
        // given
        Chain chain = new Chain();
        chain.setId(1L);
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.insert(any(ChainNode.class))).thenAnswer(inv -> {
            ChainNode n = inv.getArgument(0);
            n.setId(2L);
            return 1;
        });

        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(1L);
        request.setNodeName("电芯");
        request.setDescription("动力电池核心组件");

        // when
        NodeVO vo = chainService.createNode(request);

        // then：description 原样透传到 VO
        assertEquals("动力电池核心组件", vo.getDescription());
    }

    @Test
    void should_expose_null_stage_type_for_ordinary_node() {
        Chain chain = chain(1L, "新能源汽车");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.insert(any(ChainNode.class)))
                .thenAnswer(invocation -> {
                    ChainNode node = invocation.getArgument(0);
                    node.setId(10L);
                    return 1;
                });
        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(1L);
        request.setNodeName("普通节点");

        NodeVO result = chainService.createNode(request);

        assertNull(result.getStageType());
    }

    @Test
    void should_update_fields_when_update_node() {
        // given
        ChainNode node = new ChainNode();
        node.setId(3L);
        node.setNodeName("原材料");
        node.setSortOrder(1);
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setNodeName("原材料（改）");
        request.setKeywords("锂矿,锂辉石,碳酸锂");
        request.setSortOrder(2);

        // when
        NodeVO vo = chainService.updateNode(3L, request);

        // then
        assertEquals("原材料（改）", vo.getNodeName());
        assertEquals("锂矿,锂辉石,碳酸锂", vo.getKeywords());
        assertEquals(2, vo.getSortOrder());
        verify(chainNodeMapper, times(1)).updateById(any(ChainNode.class));
    }

    @Test
    void should_normalize_keywords_when_update_node() {
        // given
        ChainNode node = new ChainNode();
        node.setId(3L);
        node.setNodeName("原材料");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setKeywords(" 锂矿 , 锂辉石 ,, 锂矿 , 碳酸锂 ");

        // when
        NodeVO vo = chainService.updateNode(3L, request);

        // then
        assertEquals("锂矿,锂辉石,碳酸锂", vo.getKeywords());
    }

    @Test
    void should_preserve_existing_keywords_when_update_node_keywords_null() {
        // given：updateNode 部分更新，keywords 为 null 不应覆盖既有值
        ChainNode node = new ChainNode();
        node.setId(3L);
        node.setNodeName("原材料");
        node.setKeywords("锂矿,锂辉石");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setSortOrder(5);

        // when
        NodeVO vo = chainService.updateNode(3L, request);

        // then：原 keywords 保留
        assertEquals("锂矿,锂辉石", vo.getKeywords());
    }

    @Test
    void should_reject_parent_change_when_update_stage_node() {
        ChainNode stage = rootNode(2L, "上游");
        stage.setStageType(ChainStageType.UPSTREAM);
        when(chainNodeMapper.selectById(2L)).thenReturn(stage);

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setNodeName("供应端");
        request.setParentChangeRequested(true);
        request.setParentId(9L);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.updateNode(2L, request));

        assertEquals(400, ex.getCode());
        assertEquals("阶段节点无法修改父节点", ex.getMessage());
        verify(chainNodeMapper, never()).updateSubtree(
                anyLong(), anyLong(), any(), any(),
                any(), any(), any(), any());
        verify(chainNodeMapper, never()).updateById(any(ChainNode.class));
    }

    @Test
    void should_update_fields_and_parent_in_one_request() {
        ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
        ChainNode target = rootNode(9L, "中游");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.selectById(9L)).thenReturn(target);
        when(chainNodeMapper.updateSubtree(
                1L, 3L, 9L, 4,
                "/2/3/", "/2/3/%", "/9/3/", 0))
                .thenReturn(2);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setNodeName("动力电池");
        request.setKeywords("动力电池,电芯");
        request.setSortOrder(4);
        request.setParentChangeRequested(true);
        request.setParentId(9L);

        NodeVO result = chainService.updateNode(3L, request);

        assertEquals(9L, result.getParentId());
        assertEquals("/9/3/", result.getNodePath());
        assertEquals("动力电池", result.getNodeName());
        assertEquals("动力电池,电芯", result.getKeywords());
        verify(chainNodeMapper).updateSubtree(
                1L, 3L, 9L, 4,
                "/2/3/", "/2/3/%", "/9/3/", 0);
        verify(chainNodeMapper).updateById(node);
    }

    @Test
    void should_not_move_when_parent_change_is_not_requested() {
        ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setNodeName("改名节点");
        request.setParentId(9L);

        NodeVO result = chainService.updateNode(3L, request);

        assertEquals(2L, result.getParentId());
        verify(chainNodeMapper, never()).updateSubtree(
                anyLong(), anyLong(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void should_move_to_root_when_parent_change_requested_with_null_parent() {
        ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateSubtree(
                1L, 3L, null, null,
                "/2/3/", "/2/3/%", "/3/", -1))
                .thenReturn(1);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setParentChangeRequested(true);
        request.setParentId(null);

        NodeVO result = chainService.updateNode(3L, request);

        assertNull(result.getParentId());
        assertEquals(1, result.getNodeLevel());
        assertEquals("/3/", result.getNodePath());
    }

    @Test
    void should_fail_when_node_update_affects_no_rows() {
        ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(0);

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setNodeName("更新失败节点");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.updateNode(3L, request));

        assertEquals(500, ex.getCode());
        assertEquals("节点更新失败", ex.getMessage());
    }

    @Test
    void should_not_update_fields_when_atomic_move_fails() {
        ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateSubtree(
                1L, 3L, null, null,
                "/2/3/", "/2/3/%", "/3/", -1))
                .thenReturn(0);

        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setNodeName("不应落库");
        request.setParentChangeRequested(true);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.updateNode(3L, request));

        assertEquals(404, ex.getCode());
        verify(chainNodeMapper, never()).updateById(any(ChainNode.class));
    }

    // ==================== 5.5 关键词搜索 ====================

    @Test
    void should_throw_bad_request_when_search_by_keywords_blank() {
        // null 关键词
        BusinessException nullEx = assertThrows(BusinessException.class,
                () -> chainService.searchByKeywords(null, null, 1, 20));
        assertEquals(400, nullEx.getCode());
        assertEquals("搜索关键词不能为空", nullEx.getMessage());

        // 空白关键词
        BusinessException blankEx = assertThrows(BusinessException.class,
                () -> chainService.searchByKeywords("   ", null, 1, 20));
        assertEquals(400, blankEx.getCode());

        verify(chainNodeMapper, never())
                .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void should_return_mapped_node_vos_when_search_by_keywords() {
        // given：命中一个带 keywords/description 的节点
        ChainNode node = new ChainNode();
        node.setId(7L);
        node.setChainId(1L);
        node.setNodeName("电芯");
        node.setKeywords("动力电池,电芯,模组");
        node.setDescription("动力电池核心组件");
        node.setNodeLevel(2);
        when(chainNodeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<ChainNode> page = invocation.getArgument(0);
                    page.setRecords(List.of(node));
                    page.setTotal(1L);
                    return page;
                });

        // when：限定产业链 1
        PageResult<NodeVO> result =
                chainService.searchByKeywords("电芯", 1L, 1, 20);

        // then
        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
        assertEquals(1L, result.getPageNum());
        assertEquals(20L, result.getPageSize());
        NodeVO vo = result.getRecords().get(0);
        assertEquals(7L, vo.getId());
        assertEquals("动力电池,电芯,模组", vo.getKeywords());
        assertEquals("动力电池核心组件", vo.getDescription());
        verify(chainNodeMapper, times(1))
                .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void should_search_across_all_chains_when_chain_id_null() {
        // given：chainId 为 null 时不按产业链过滤，仅按关键词模糊匹配
        ChainNode node = new ChainNode();
        node.setId(8L);
        node.setChainId(2L);
        node.setNodeName("电芯");
        node.setKeywords("电芯,模组");
        node.setNodeLevel(1);
        when(chainNodeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<ChainNode> page = invocation.getArgument(0);
                    page.setRecords(List.of(node));
                    page.setTotal(1L);
                    return page;
                });

        // when
        PageResult<NodeVO> result =
                chainService.searchByKeywords("电芯", null, 1, 20);

        // then：不抛异常，正常返回
        assertEquals(1, result.getRecords().size());
        assertEquals(2L, result.getRecords().get(0).getChainId());
    }

    @Test
    void should_return_empty_page_when_no_keyword_match() {
        // given
        when(chainNodeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<ChainNode> page = invocation.getArgument(0);
                    page.setRecords(Collections.emptyList());
                    page.setTotal(0L);
                    return page;
                });

        // when
        PageResult<NodeVO> result =
                chainService.searchByKeywords("不存在的关键词", null, 1, 20);

        // then
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0L, result.getTotal());
    }

    @Test
    void should_reject_move_when_node_is_stage() {
        ChainNode stage = rootNode(2L, "供应端");
        stage.setStageType(ChainStageType.UPSTREAM);
        when(chainNodeMapper.selectById(2L)).thenReturn(stage);
        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.moveNode(2L, request));

        assertEquals(400, ex.getCode());
        assertEquals("阶段节点无法修改父节点", ex.getMessage());
        verify(chainNodeMapper, never()).updateSubtree(
                anyLong(), anyLong(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void should_reject_delete_stage_before_child_check() {
        ChainNode stage = rootNode(2L, "供应端");
        stage.setStageType(ChainStageType.UPSTREAM);
        when(chainNodeMapper.selectById(2L)).thenReturn(stage);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.deleteNode(2L));

        assertEquals("上下游阶段节点不允许删除", ex.getMessage());
        verify(chainNodeMapper, never()).countChildren(anyLong());
    }

    @Test
    void should_allow_rename_without_changing_stage_type() {
        ChainNode stage = rootNode(2L, "上游");
        stage.setStageType(ChainStageType.UPSTREAM);
        when(chainNodeMapper.selectById(2L)).thenReturn(stage);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);
        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setNodeName("原材料端");

        NodeVO result = chainService.updateNode(2L, request);

        assertEquals("原材料端", result.getNodeName());
        assertEquals(ChainStageType.UPSTREAM, result.getStageType());
    }

    @Test
    void should_reject_null_node_id() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.deleteNode(null));

        assertEquals(400, ex.getCode());
        assertEquals("节点ID不能为空", ex.getMessage());
        verify(chainNodeMapper, never()).selectById(any());
    }

    @Test
    void should_reject_blank_node_name_on_update() {
        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setNodeName("   ");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.updateNode(2L, request));

        assertEquals("节点名称不能为空", ex.getMessage());
        verify(chainNodeMapper, never()).selectById(any());
    }

    @Test
    void should_reject_negative_sort_order_when_create_node() {
        Chain chain = chain(1L, "新能源汽车");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.insert(any(ChainNode.class))).thenAnswer(invocation -> {
            ChainNode node = invocation.getArgument(0);
            node.setId(10L);
            return 1;
        });
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);
        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(1L);
        request.setNodeName("测试节点");
        request.setSortOrder(-1);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.createNode(request));

        assertEquals(400, ex.getCode());
        assertEquals("排序值不能小于 0", ex.getMessage());
        verify(chainNodeMapper, never()).insert(any(ChainNode.class));
    }

    @Test
    void should_reject_negative_sort_order_when_update_node() {
        ChainNode node = movableNode(3L, 1L, null, 1, "/3/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);
        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setSortOrder(-1);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.updateNode(3L, request));

        assertEquals(400, ex.getCode());
        assertEquals("排序值不能小于 0", ex.getMessage());
        verify(chainNodeMapper, never()).updateById(any(ChainNode.class));
    }

    @Test
    void should_reject_empty_node_orders_in_service() {
        SortNodesRequest request = new SortNodesRequest();
        request.setNodeOrders(List.of());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.sortNodes(request));

        assertEquals("排序节点不能为空", ex.getMessage());
    }
    // ==================== 6. 节点移动 / 排序 ====================

    @Test
    void should_reject_noncanonical_source_path_before_bulk_move() {
        ChainNode node = movableNode(3L, 1L, null, 1, "/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);

        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.moveNode(3L, request));

        assertEquals(500, exception.getCode());
        assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
        verify(chainNodeMapper, never()).updateSubtree(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void should_allow_moving_root_node_under_canonical_sibling() {
        ChainNode node = movableNode(3L, 1L, null, 1, "/3/");
        ChainNode sibling = movableNode(5L, 1L, null, 1, "/5/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.selectById(5L)).thenReturn(sibling);
        when(chainNodeMapper.updateSubtree(
                1L, 3L, 5L, 0,
                "/3/", "/3/%", "/5/3/", 1))
                .thenReturn(1);

        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(5L);
        request.setSortOrder(0);

        NodeVO result = chainService.moveNode(3L, request);

        assertEquals(5L, result.getParentId());
        assertEquals("/5/3/", result.getNodePath());
        assertEquals(2, result.getNodeLevel());
    }

    @Test
    void should_reject_move_when_target_ancestor_is_missing() {
        ChainNode node = movableNode(3L, 1L, null, 1, "/3/");
        ChainNode target = movableNode(5L, 1L, 99L, 2, "/99/5/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.selectById(5L)).thenReturn(target);
        when(chainNodeMapper.selectById(99L)).thenReturn(null);

        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.moveNode(3L, request));

        assertEquals(500, exception.getCode());
        assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
        verify(chainNodeMapper, never()).updateSubtree(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void should_reject_move_when_target_ancestor_chain_has_cycle() {
        ChainNode node = movableNode(3L, 1L, null, 1, "/3/");
        ChainNode target = movableNode(5L, 1L, 6L, 2, "/6/5/");
        ChainNode cyclicAncestor = movableNode(6L, 1L, 5L, 2, "/5/6/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.selectById(5L)).thenReturn(target);
        when(chainNodeMapper.selectById(6L)).thenReturn(cyclicAncestor);

        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.moveNode(3L, request));

        assertEquals(500, exception.getCode());
        assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
        verify(chainNodeMapper, never()).updateSubtree(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void should_reject_move_when_target_ancestor_belongs_to_another_chain() {
        ChainNode node = movableNode(3L, 1L, null, 1, "/3/");
        ChainNode target = movableNode(5L, 1L, 99L, 2, "/99/5/");
        ChainNode foreignAncestor = movableNode(99L, 2L, null, 1, "/99/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.selectById(5L)).thenReturn(target);
        when(chainNodeMapper.selectById(99L)).thenReturn(foreignAncestor);

        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.moveNode(3L, request));

        assertEquals(500, exception.getCode());
        assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
        verify(chainNodeMapper, never()).updateSubtree(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void should_move_node_and_rewrite_descendant_paths() {
        // given：上游(2,/2/) 下有原材料(3,/2/3/)，移动到中游(5,/5/) 下
        ChainNode node = new ChainNode();
        node.setId(3L);
        node.setChainId(1L);
        node.setParentId(2L);
        node.setNodeName("原材料");
        node.setNodeLevel(2);
        node.setNodePath("/2/3/");
        node.setSortOrder(1);

        ChainNode newParent = new ChainNode();
        newParent.setId(5L);
        newParent.setChainId(1L);
        newParent.setNodeName("中游");
        newParent.setNodeLevel(1);
        newParent.setNodePath("/5/");

        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.selectById(5L)).thenReturn(newParent);
        when(chainNodeMapper.updateSubtree(
                1L, 3L, 5L, 2,
                "/2/3/", "/2/3/%", "/5/3/", 0))
                .thenReturn(2);

        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(5L);
        request.setSortOrder(2);

        // when
        NodeVO vo = chainService.moveNode(3L, request);

        // then
        assertEquals(5L, vo.getParentId());
        assertEquals("/5/3/", vo.getNodePath());
        assertEquals(2, vo.getNodeLevel());
        verify(chainNodeMapper).updateSubtree(
                1L, 3L, 5L, 2,
                "/2/3/", "/2/3/%", "/5/3/", 0);
        verify(chainNodeMapper, never())
                .selectList(any(LambdaQueryWrapper.class));
        verify(chainNodeMapper, never()).updateById(any(ChainNode.class));
    }

    @Test
    void should_move_node_to_root_without_changing_sort_order() {
        ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
        node.setSortOrder(5);
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateSubtree(
                1L, 3L, null, null,
                "/2/3/", "/2/3/%", "/3/", -1))
                .thenReturn(2);
        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(null);

        NodeVO vo = chainService.moveNode(3L, request);

        assertNull(vo.getParentId());
        assertEquals("/3/", vo.getNodePath());
        assertEquals(1, vo.getNodeLevel());
        assertEquals(5, vo.getSortOrder());
    }

    @Test
    void should_throw_not_found_when_bulk_move_updates_no_rows() {
        ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateSubtree(
                1L, 3L, null, null,
                "/2/3/", "/2/3/%", "/3/", -1))
                .thenReturn(0);
        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chainService.moveNode(3L, request));

        assertEquals(404, exception.getCode());
        assertEquals("节点不存在", exception.getMessage());
    }

    @Test
    void should_throw_bad_request_when_move_node_to_descendant() {
        // given：节点 3 的路径为 /2/3/，目标父节点 4 在其子树 /2/3/4/ 内
        ChainNode node = new ChainNode();
        node.setId(3L);
        node.setChainId(1L);
        node.setNodeLevel(2);
        node.setNodePath("/2/3/");

        ChainNode descendant = new ChainNode();
        descendant.setId(4L);
        descendant.setChainId(1L);
        descendant.setParentId(3L);
        descendant.setNodeLevel(3);
        descendant.setNodePath("/2/3/4/");

        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.selectById(4L)).thenReturn(descendant);

        MoveNodeRequest request = new MoveNodeRequest();
        request.setNewParentId(4L);

        // when
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chainService.moveNode(3L, request));

        // then
        assertEquals(400, ex.getCode());
        assertEquals("节点不能移动到自己的子节点下", ex.getMessage());
        verify(chainNodeMapper, times(2)).selectById(3L);
        verify(chainNodeMapper, never()).updateSubtree(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void should_update_sort_order_when_sort_nodes() {
        // given：同一父节点下两个节点重新排序
        SortNodeOrderRequest first = new SortNodeOrderRequest();
        first.setNodeId(2L);
        first.setSortOrder(2);

        SortNodeOrderRequest second = new SortNodeOrderRequest();
        second.setNodeId(3L);
        second.setSortOrder(1);

        SortNodesRequest request = new SortNodesRequest();
        request.setParentId(1L);
        request.setNodeOrders(List.of(first, second));
        when(chainNodeMapper.update(
                any(ChainNode.class),
                any(LambdaUpdateWrapper.class)))
                .thenReturn(1);

        // when
        chainService.sortNodes(request);

        // then
        verify(chainNodeMapper, times(2)).update(any(ChainNode.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void should_throw_not_found_when_sort_node_update_matches_no_row() {
        SortNodeOrderRequest first = new SortNodeOrderRequest();
        first.setNodeId(2L);
        first.setSortOrder(1);
        SortNodeOrderRequest missing = new SortNodeOrderRequest();
        missing.setNodeId(999L);
        missing.setSortOrder(2);

        SortNodesRequest request = new SortNodesRequest();
        request.setParentId(1L);
        request.setNodeOrders(List.of(first, missing));
        when(chainNodeMapper.update(
                any(ChainNode.class),
                any(LambdaUpdateWrapper.class)))
                .thenReturn(1, 0);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.sortNodes(request));

        assertEquals(404, ex.getCode());
        assertEquals("排序节点不存在或不属于指定父节点", ex.getMessage());
        verify(chainNodeMapper, times(2))
                .update(any(ChainNode.class), any(LambdaUpdateWrapper.class));
    }

    private NodeEnterpriseCountRow countRow(Long nodeId, Long directCount) {
        NodeEnterpriseCountRow row = new NodeEnterpriseCountRow();
        row.setNodeId(nodeId);
        row.setDirectEnterpriseCount(directCount);
        return row;
    }

    private NodeSubtreeEnterpriseCountRow subtreeCountRow(
            Long nodeId,
            Long subtreeCount) {
        NodeSubtreeEnterpriseCountRow row =
                new NodeSubtreeEnterpriseCountRow();
        row.setNodeId(nodeId);
        row.setSubtreeEnterpriseCount(subtreeCount);
        return row;
    }

    private ChainSummaryCountRow summaryCountRow(
            Long chainId,
            Long nodeCount,
            Long enterpriseCount) {
        ChainSummaryCountRow row = new ChainSummaryCountRow();
        row.setChainId(chainId);
        row.setNodeCount(nodeCount);
        row.setEnterpriseCount(enterpriseCount);
        return row;
    }

    private Chain chain(Long id, String name) {
        Chain chain = new Chain();
        chain.setId(id);
        chain.setChainName(name);
        return chain;
    }

    private ChainNode rootNode(Long id, String name) {
        ChainNode node = new ChainNode();
        node.setId(id);
        node.setChainId(1L);
        node.setParentId(null);
        node.setNodeName(name);
        node.setNodeLevel(1);
        node.setNodePath("/" + id + "/");
        node.setSortOrder(Math.toIntExact(id));
        return node;
    }

    private ChainNode movableNode(
            Long id,
            Long chainId,
            Long parentId,
            int nodeLevel,
            String nodePath) {
        ChainNode node = new ChainNode();
        node.setId(id);
        node.setChainId(chainId);
        node.setParentId(parentId);
        node.setNodeName("待移动节点");
        node.setNodeLevel(nodeLevel);
        node.setNodePath(nodePath);
        node.setSortOrder(1);
        return node;
    }
}
