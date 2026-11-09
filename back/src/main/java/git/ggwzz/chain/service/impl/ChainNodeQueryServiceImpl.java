package git.ggwzz.chain.service.impl;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.service.ChainNodeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产业链节点查询服务实现。
 *
 * <p>为智能挂链模块提供候选节点列表和节点路径。</p>
 */
@Service
@RequiredArgsConstructor
public class ChainNodeQueryServiceImpl implements ChainNodeQueryService {

    private final ChainNodeMapper chainNodeMapper;
    private final ChainMapper chainMapper;
    private final ChainNodeTreeRules chainNodeTreeRules = new ChainNodeTreeRules();

    @Override
    public List<ChainNodeCandidateDTO> listByChainId(Long chainId) {
        return chainNodeMapper.selectByChainId(chainId).stream()
                .map(this::toChainNodeCandidate)
                .toList();
    }

    @Override
    public String buildNodePath(Long nodeId) {
        if (nodeId == null) {
            return null;
        }
        ChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null) {
            return null;
        }

        Chain chain = chainMapper.selectById(node.getChainId());
        String chainName = chain == null ? null : chain.getChainName();
        List<Long> pathIds = chainNodeTreeRules.parseNodePath(node.getNodePath());
        if (pathIds.isEmpty()) {
            pathIds.add(node.getId());
        }
        Map<Long, ChainNode> nodeMap = loadNodesById(pathIds);
        return chainNodeTreeRules.buildDisplayPath(node, chainName, nodeMap.values());
    }

    @Override
    public Map<Long, String> buildNodePathMap(Collection<Long> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> requestedIds = nodeIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ChainNode> requestedNodeMap = loadNodesById(requestedIds);
        Set<Long> chainIds = requestedNodeMap.values().stream()
                .map(ChainNode::getChainId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> pathNodeIds = new LinkedHashSet<>();
        for (ChainNode node : requestedNodeMap.values()) {
            List<Long> pathIds = chainNodeTreeRules.parseNodePath(node.getNodePath());
            if (pathIds.isEmpty() && node.getId() != null) {
                pathIds.add(node.getId());
            }
            pathNodeIds.addAll(pathIds);
        }

        Map<Long, ChainNode> pathNodeMap = loadNodesById(pathNodeIds);
        Map<Long, String> chainNameMap = loadChainNameMap(chainIds);
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long nodeId : requestedIds) {
            ChainNode node = requestedNodeMap.get(nodeId);
            if (node == null) {
                continue;
            }
            result.put(nodeId, chainNodeTreeRules.buildDisplayPath(
                    node, chainNameMap.get(node.getChainId()), pathNodeMap.values()));
        }
        return result;
    }

    private ChainNodeCandidateDTO toChainNodeCandidate(ChainNode node) {
        ChainNodeCandidateDTO candidate = new ChainNodeCandidateDTO();
        candidate.setNodeId(node.getId());
        candidate.setChainId(node.getChainId());
        candidate.setNodeName(node.getNodeName());
        candidate.setNodePath(buildNodePath(node.getId()));
        candidate.setKeywords(node.getKeywords());
        candidate.setLevel(node.getNodeLevel());
        return candidate;
    }

    private Map<Long, ChainNode> loadNodesById(Collection<Long> nodeIds) {
        Map<Long, ChainNode> result = new HashMap<>();
        if (nodeIds.isEmpty()) {
            return result;
        }
        for (ChainNode node : chainNodeMapper.selectBatchIds(nodeIds)) {
            result.put(node.getId(), node);
        }
        return result;
    }

    private Map<Long, String> loadChainNameMap(Collection<Long> chainIds) {
        Map<Long, String> result = new HashMap<>();
        if (chainIds == null || chainIds.isEmpty()) {
            return result;
        }
        for (Chain chain : chainMapper.selectBatchIds(chainIds)) {
            if (chain.getId() != null && chain.getChainName() != null) {
                result.put(chain.getId(), chain.getChainName());
            }
        }
        return result;
    }
}
