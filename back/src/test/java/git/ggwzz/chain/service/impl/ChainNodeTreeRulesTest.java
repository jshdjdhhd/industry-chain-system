package git.ggwzz.chain.service.impl;

import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.enums.ChainStageType;
import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChainNodeTreeRulesTest {

    private final ChainNodeTreeRules rules = new ChainNodeTreeRules();

    @Test
    void planMove_shouldBuildBulkUpdateArgumentsAndApplyResultToNode() {
        ChainNode node = node(3L, 1L, 2L, 2, "/2/3/");
        ChainNode targetParent = node(9L, 1L, null, 1, "/9/");

        ChainNodeTreeRules.MovePlan plan = rules.planMove(
                node,
                9L,
                4,
                id -> id.equals(9L) ? targetParent : null,
                "阶段节点无法修改父节点");

        assertEquals(1L, plan.chainId());
        assertEquals(3L, plan.nodeId());
        assertEquals(9L, plan.newParentId());
        assertEquals(4, plan.sortOrder());
        assertEquals("/2/3/", plan.oldPath());
        assertEquals("/2/3/%", plan.oldPathPattern());
        assertEquals("/9/3/", plan.newPath());
        assertEquals(0, plan.levelOffset());
        assertEquals(2, plan.newLevel());

        plan.applyTo(node);
        assertEquals(9L, node.getParentId());
        assertEquals("/9/3/", node.getNodePath());
        assertEquals(2, node.getNodeLevel());
        assertEquals(4, node.getSortOrder());
    }

    @Test
    void planMove_shouldRejectStageNodeBeforeLoadingTargetParent() {
        ChainNode stage = node(2L, 1L, null, 1, "/2/");
        stage.setStageType(ChainStageType.UPSTREAM);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rules.planMove(
                        stage,
                        9L,
                        null,
                        id -> {
                            throw new AssertionError("target parent must not be loaded for stage nodes");
                        },
                        "阶段节点无法修改父节点"));

        assertEquals(400, exception.getCode());
        assertEquals("阶段节点无法修改父节点", exception.getMessage());
    }

    @Test
    void planMove_shouldRejectNonCanonicalSourcePath() {
        ChainNode node = node(3L, 1L, null, 1, "/");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rules.planMove(
                        node,
                        null,
                        null,
                        id -> null,
                        "阶段节点无法修改父节点"));

        assertEquals(500, exception.getCode());
        assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
    }

    @Test
    void planMove_shouldRejectTargetAncestorCycle() {
        ChainNode node = node(3L, 1L, null, 1, "/3/");
        ChainNode target = node(5L, 1L, 6L, 2, "/6/5/");
        ChainNode cyclicAncestor = node(6L, 1L, 5L, 2, "/5/6/");
        Map<Long, ChainNode> nodes = Map.of(5L, target, 6L, cyclicAncestor);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rules.planMove(
                        node,
                        5L,
                        null,
                        nodes::get,
                        "阶段节点无法修改父节点"));

        assertEquals(500, exception.getCode());
        assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
    }

    @Test
    void buildDisplayPath_shouldJoinChainAndPathNodesInMaterializedPathOrder() {
        ChainNode current = node(102L, 1L, 101L, 2, "/100/101/102/");
        current.setNodeName("Battery Manufacturing");
        ChainNode root = node(100L, 1L, null, 1, "/100/");
        root.setNodeName("New Energy Vehicle");
        ChainNode parent = node(101L, 1L, 100L, 2, "/100/101/");
        parent.setNodeName("Midstream");

        String result = rules.buildDisplayPath(
                current,
                "新能源汽车产业链",
                List.of(current, parent, root));

        assertEquals("新能源汽车产业链 / New Energy Vehicle / Midstream / Battery Manufacturing", result);
    }

    @Test
    void buildDisplayPath_shouldFallbackToCurrentNodeNameWhenPathIsBlankAndChainMissing() {
        ChainNode current = node(102L, 1L, null, 1, " ");
        current.setNodeName("Battery Manufacturing");

        String result = rules.buildDisplayPath(current, null, List.of(current));

        assertEquals("Battery Manufacturing", result);
    }

    @Test
    void parseNodePath_shouldSkipBlankAndUnparseableSegments() {
        assertEquals(List.of(101L, 102L), rules.parseNodePath("/101/not-a-number/102//"));
        assertEquals(List.of(), rules.parseNodePath("  "));
        assertEquals(List.of(), rules.parseNodePath(null));
    }

    @Test
    void planMove_shouldKeepSortOrderWhenSortOrderIsNull() {
        ChainNode node = node(3L, 1L, 2L, 2, "/2/3/");
        node.setSortOrder(7);

        ChainNodeTreeRules.MovePlan plan = rules.planMove(
                node,
                null,
                null,
                id -> null,
                "阶段节点无法修改父节点");

        plan.applyTo(node);
        assertNull(node.getParentId());
        assertEquals("/3/", node.getNodePath());
        assertEquals(1, node.getNodeLevel());
        assertEquals(7, node.getSortOrder());
    }

    private ChainNode node(Long id, Long chainId, Long parentId, Integer level, String path) {
        ChainNode node = new ChainNode();
        node.setId(id);
        node.setChainId(chainId);
        node.setParentId(parentId);
        node.setNodeName("node-" + id);
        node.setNodeLevel(level);
        node.setNodePath(path);
        return node;
    }
}
