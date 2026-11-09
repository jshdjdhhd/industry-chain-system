package git.ggwzz.chain.service.impl;

import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 产业链节点树规则 module。
 *
 * <p>集中 node_path canonical 校验、节点移动计划、祖先链校验和展示路径拼接。
 * 该 module 不直接访问数据库；caller 通过函数参数提供节点加载能力。</p>
 */
@Slf4j
public class ChainNodeTreeRules {

    private static final String PATH_SEPARATOR = " / ";

    public MovePlan planMove(ChainNode node,
                             Long newParentId,
                             Integer sortOrder,
                             Function<Long, ChainNode> nodeLoader,
                             String stageErrorMessage) {
        requireOrdinaryNode(node, stageErrorMessage);
        if (Objects.equals(node.getId(), newParentId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "节点不能移动到自身下");
        }

        String oldPath = requireCanonicalNodePath(node);
        int oldLevel = node.getNodeLevel();
        String parentPath = "/";
        int newLevel = 1;
        if (newParentId != null) {
            ChainNode parent = nodeLoader.apply(newParentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "父节点不存在");
            }
            if (!Objects.equals(node.getChainId(), parent.getChainId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不能跨产业链移动节点");
            }
            validateMoveTarget(node, parent, nodeLoader);
            parentPath = requireCanonicalNodePath(parent);
            newLevel = parent.getNodeLevel() + 1;
        }

        String newPath = parentPath + node.getId() + "/";
        int levelOffset = newLevel - oldLevel;
        return new MovePlan(
                node.getChainId(),
                node.getId(),
                newParentId,
                sortOrder,
                oldPath,
                oldPath + "%",
                newPath,
                levelOffset,
                newLevel);
    }

    public void requireOrdinaryNode(ChainNode node, String message) {
        if (node.getStageType() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
    }

    public String requireCanonicalNodePath(ChainNode node) {
        String nodePath = node.getNodePath();
        String expectedSuffix = "/" + node.getId() + "/";
        if (nodePath == null
                || "/".equals(nodePath)
                || !nodePath.startsWith("/")
                || !nodePath.endsWith(expectedSuffix)) {
            log.error(
                    "产业链节点路径异常: nodeId={}, chainId={}, parentId={}, nodePath={}",
                    node.getId(),
                    node.getChainId(),
                    node.getParentId(),
                    nodePath);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "节点层级数据异常，请联系管理员");
        }
        return nodePath;
    }

    public List<Long> parseNodePath(String nodePath) {
        List<Long> result = new ArrayList<>();
        if (nodePath == null || nodePath.isBlank()) {
            return result;
        }
        String[] parts = nodePath.split("/");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                result.add(Long.valueOf(part));
            } catch (NumberFormatException ignored) {
                // Ignore invalid path segments and keep the parseable portion.
            }
        }
        return result;
    }

    public String buildDisplayPath(ChainNode current, String chainName, Collection<ChainNode> pathNodes) {
        List<String> names = new ArrayList<>();
        if (chainName != null && !chainName.isBlank()) {
            names.add(chainName);
        }

        List<Long> pathIds = parseNodePath(current.getNodePath());
        if (pathIds.isEmpty() && current.getId() != null) {
            pathIds.add(current.getId());
        }
        Map<Long, String> nodeNames = pathNodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> node.getId() != null)
                .collect(Collectors.toMap(
                        ChainNode::getId,
                        ChainNode::getNodeName,
                        (left, right) -> left));
        for (Long pathId : pathIds) {
            String nodeName = nodeNames.get(pathId);
            if (nodeName != null && !nodeName.isBlank()) {
                names.add(nodeName);
            }
        }

        if (names.isEmpty()) {
            return current.getNodeName();
        }
        return String.join(PATH_SEPARATOR, names);
    }

    private void validateMoveTarget(ChainNode node,
                                    ChainNode targetParent,
                                    Function<Long, ChainNode> nodeLoader) {
        Set<Long> visited = new HashSet<>();
        ChainNode current = targetParent;
        while (current != null) {
            requireCanonicalNodePath(current);
            if (!Objects.equals(node.getChainId(), current.getChainId())) {
                throwCorruptTree(node.getChainId(), current.getId(), "产业链节点祖先链跨产业链");
            }
            if (!visited.add(current.getId())) {
                log.error("产业链节点祖先链存在环: chainId={}, nodeId={}", node.getChainId(), current.getId());
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "节点层级数据异常，请联系管理员");
            }
            if (Objects.equals(node.getId(), current.getId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "节点不能移动到自己的子节点下");
            }
            Long childId = current.getId();
            Long parentId = current.getParentId();
            if (parentId == null) {
                return;
            }
            current = nodeLoader.apply(parentId);
            if (current == null) {
                log.error(
                        "产业链节点祖先不存在: chainId={}, childId={}, missingParentId={}",
                        node.getChainId(),
                        childId,
                        parentId);
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "节点层级数据异常，请联系管理员");
            }
        }
    }

    private void throwCorruptTree(Long chainId, Long nodeId, String reason) {
        log.error("{}: chainId={}, nodeId={}", reason, chainId, nodeId);
        throw new BusinessException(ResultCode.INTERNAL_ERROR, "节点层级数据异常，请联系管理员");
    }

    public record MovePlan(
            Long chainId,
            Long nodeId,
            Long newParentId,
            Integer sortOrder,
            String oldPath,
            String oldPathPattern,
            String newPath,
            Integer levelOffset,
            Integer newLevel) {

        public void applyTo(ChainNode node) {
            node.setParentId(newParentId);
            node.setNodePath(newPath);
            node.setNodeLevel(newLevel);
            if (sortOrder != null) {
                node.setSortOrder(sortOrder);
            }
        }
    }
}
