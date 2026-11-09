package git.ggwzz.analysis.service;

import git.ggwzz.analysis.mapper.AnalysisQueryMapper;
import git.ggwzz.analysis.mapper.projection.EnterpriseProfileRow;
import git.ggwzz.analysis.service.impl.ChainDiagnosisTreeServiceImpl;
import git.ggwzz.analysis.vo.DiagnosisTreeNodeVO;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.chain.mapper.projection.NodeEnterpriseCountRow;
import git.ggwzz.chain.mapper.projection.NodeSubtreeEnterpriseCountRow;
import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link ChainDiagnosisTreeServiceImpl} 诊断树组装与口径一致性单元测试。
 *
 * <p>纯 Mockito 隔离 Mapper，验证树结构、子树企业数与现有
 * {@code /api/chains/{chainId}/tree} 口径一致，并校验健康度字段填充。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChainDiagnosisTreeServiceTest {

    @Mock
    private ChainMapper chainMapper;

    @Mock
    private ChainNodeMapper chainNodeMapper;

    @Mock
    private EnterpriseNodeRelMapper enterpriseNodeRelMapper;

    @Mock
    private AnalysisQueryMapper analysisQueryMapper;

    private ChainDiagnosisTreeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChainDiagnosisTreeServiceImpl(
                chainMapper,
                chainNodeMapper,
                enterpriseNodeRelMapper,
                analysisQueryMapper,
                new ChainHealthMetricService());
    }

    @Test
    void should_throw_not_found_when_chain_absent() {
        when(chainMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.getDiagnosisTree(99L));

        assertEquals(404, ex.getCode());
        assertEquals("产业链不存在", ex.getMessage());
    }

    @Test
    void should_build_diagnosis_tree_with_health_fields() {
        // 上游(2, /2/) -> 原材料(3, /2/3/)
        Chain chain = chain(1L, "新能源汽车");
        ChainNode up = node(2L, 1L, null, 1, "/2/", "上游");
        ChainNode raw = node(3L, 1L, 2L, 2, "/2/3/", "原材料");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectByChainId(1L))
                .thenReturn(List.of(up, raw));
        when(enterpriseNodeRelMapper.selectDirectCountsByChainId(1L))
                .thenReturn(List.of(
                        directCount(2L, 1L),
                        directCount(3L, 2L)));
        when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
                .thenReturn(List.of(
                        subtreeCount(2L, 3L),
                        subtreeCount(3L, 2L)));
        when(analysisQueryMapper.selectEnterpriseProfilesByChainId(1L))
                .thenReturn(List.of(
                        profile(10L, 2L, "/2/"),
                        profile(11L, 3L, "/2/3/"),
                        profile(12L, 3L, "/2/3/")));

        DiagnosisTreeNodeVO root = service.getDiagnosisTree(1L);

        // 虚拟根
        assertEquals(1L, root.getNodeId());
        assertEquals("新能源汽车", root.getNodeName());
        assertEquals(0, root.getNodeLevel());
        assertEquals(3, root.getSubtreeEnterpriseCount(),
                "虚拟根子树企业数应为全链去重企业数");
        assertNotNull(root.getHealthLevel());
        assertNotNull(root.getHealthLabel());
        assertFalse(root.getWeakReasons() == null,
                "弱项原因列表不能为 null");

        assertEquals(1, root.getChildren().size());
        DiagnosisTreeNodeVO upVO = root.getChildren().get(0);
        assertEquals("上游", upVO.getNodeName());
        assertEquals("/2/", upVO.getNodePath());
        assertEquals(1, upVO.getDirectEnterpriseCount());
        assertEquals(3, upVO.getSubtreeEnterpriseCount(),
                "子树企业数口径应与 chain 模块一致");
        assertNotNull(upVO.getHealthScore());
        assertNotNull(upVO.getHealthLevel());
        assertNotNull(upVO.getHealthLabel());
        assertFalse(upVO.getWeakReasons() == null);

        assertEquals(1, upVO.getChildren().size());
        DiagnosisTreeNodeVO rawVO = upVO.getChildren().get(0);
        assertEquals("原材料", rawVO.getNodeName());
        assertEquals(2, rawVO.getDirectEnterpriseCount());
        assertEquals(2, rawVO.getSubtreeEnterpriseCount());
        assertNotNull(rawVO.getHealthLevel());
    }

    @Test
    void should_mark_empty_node_when_no_enterprises() {
        Chain chain = chain(1L, "空产业链");
        ChainNode leaf = node(5L, 1L, null, 1, "/5/", "无企业节点");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectByChainId(1L))
                .thenReturn(List.of(leaf));
        when(enterpriseNodeRelMapper.selectDirectCountsByChainId(1L))
                .thenReturn(List.of());
        when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
                .thenReturn(List.of());
        when(analysisQueryMapper.selectEnterpriseProfilesByChainId(1L))
                .thenReturn(List.of());

        DiagnosisTreeNodeVO root = service.getDiagnosisTree(1L);

        DiagnosisTreeNodeVO leafVO = root.getChildren().get(0);
        assertEquals(0, leafVO.getSubtreeEnterpriseCount());
        assertEquals("EMPTY", leafVO.getHealthLevel());
        assertEquals("空白", leafVO.getHealthLabel());
        assertTrue(leafVO.getWeakReasons()
                .contains("该节点及子树暂无已挂载企业"));
        assertEquals(0, root.getSubtreeEnterpriseCount());
    }

    @Test
    void should_keep_subtree_count_consistent_with_chain_module() {
        // 一级节点 100、103 各 1 家，子树数应与 selectSubtreeCountsByChainId 一致
        Chain chain = chain(1L, "新能源汽车");
        ChainNode a = node(100L, 1L, null, 1, "/100/", "中游");
        ChainNode b = node(103L, 1L, null, 1, "/103/", "整车制造");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectByChainId(1L))
                .thenReturn(List.of(a, b));
        when(enterpriseNodeRelMapper.selectDirectCountsByChainId(1L))
                .thenReturn(List.of(
                        directCount(100L, 1L),
                        directCount(103L, 1L)));
        when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
                .thenReturn(List.of(
                        subtreeCount(100L, 1L),
                        subtreeCount(103L, 1L)));
        when(analysisQueryMapper.selectEnterpriseProfilesByChainId(1L))
                .thenReturn(List.of(
                        profile(20L, 100L, "/100/"),
                        profile(21L, 103L, "/103/")));

        DiagnosisTreeNodeVO root = service.getDiagnosisTree(1L);

        assertEquals(2, root.getChildren().size());
        assertEquals(1, root.getChildren().get(0).getSubtreeEnterpriseCount());
        assertEquals(1, root.getChildren().get(1).getSubtreeEnterpriseCount());
        assertEquals(2, root.getSubtreeEnterpriseCount());
    }

    @Test
    void should_dedup_root_subtree_count_when_enterprise_cross_mounted() {
        // 跨一级节点多挂载：同一企业 20 同时挂在两个不同一级节点 100、103 子树下。
        // 一级子树数之和 = 1 + 1 = 2，但全链去重企业数 = 1，
        // 虚拟根口径须与 chain 模块 getChainTree 根（全链 COUNT(DISTINCT)）一致。
        Chain chain = chain(1L, "新能源汽车");
        ChainNode a = node(100L, 1L, null, 1, "/100/", "中游");
        ChainNode b = node(103L, 1L, null, 1, "/103/", "整车制造");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.selectByChainId(1L))
                .thenReturn(List.of(a, b));
        when(enterpriseNodeRelMapper.selectDirectCountsByChainId(1L))
                .thenReturn(List.of(
                        directCount(100L, 1L),
                        directCount(103L, 1L)));
        when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
                .thenReturn(List.of(
                        subtreeCount(100L, 1L),
                        subtreeCount(103L, 1L)));
        // 同一企业 20 在两个一级节点下各挂一次
        when(analysisQueryMapper.selectEnterpriseProfilesByChainId(1L))
                .thenReturn(List.of(
                        profile(20L, 100L, "/100/"),
                        profile(20L, 103L, "/103/")));

        DiagnosisTreeNodeVO root = service.getDiagnosisTree(1L);

        assertEquals(2, root.getChildren().size());
        assertEquals(1, root.getChildren().get(0).getSubtreeEnterpriseCount());
        assertEquals(1, root.getChildren().get(1).getSubtreeEnterpriseCount());
        // 虚拟根按全链去重企业数，不应是一级子树数之和 2
        assertEquals(1, root.getSubtreeEnterpriseCount(),
                "虚拟根子树企业数应为全链去重企业数，与 chain 模块根口径一致");
    }

    private Chain chain(Long id, String name) {
        Chain chain = new Chain();
        chain.setId(id);
        chain.setChainName(name);
        return chain;
    }

    private ChainNode node(Long id, Long chainId, Long parentId,
                           int level, String path, String name) {
        ChainNode n = new ChainNode();
        n.setId(id);
        n.setChainId(chainId);
        n.setParentId(parentId);
        n.setNodeLevel(level);
        n.setNodePath(path);
        n.setNodeName(name);
        return n;
    }

    private NodeEnterpriseCountRow directCount(Long nodeId, Long count) {
        NodeEnterpriseCountRow row = new NodeEnterpriseCountRow();
        row.setNodeId(nodeId);
        row.setDirectEnterpriseCount(count);
        return row;
    }

    private NodeSubtreeEnterpriseCountRow subtreeCount(Long nodeId, Long count) {
        NodeSubtreeEnterpriseCountRow row = new NodeSubtreeEnterpriseCountRow();
        row.setNodeId(nodeId);
        row.setSubtreeEnterpriseCount(count);
        return row;
    }

    private EnterpriseProfileRow profile(Long eid, Long nodeId, String path) {
        EnterpriseProfileRow row = new EnterpriseProfileRow();
        row.setEnterpriseId(eid);
        row.setNodeId(nodeId);
        row.setNodePath(path);
        row.setHasOutputValue(true);
        row.setHasTaxAmount(true);
        row.setHasTag(true);
        row.setHasMainProducts(true);
        row.setHasCoreTech(true);
        return row;
    }
}
