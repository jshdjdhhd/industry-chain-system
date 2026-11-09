package git.ggwzz.chain.service.impl;

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
import git.ggwzz.chain.entity.EnterpriseNodeRel;
import git.ggwzz.chain.enums.ChainStageType;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeCandidateMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.ChainOptionMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.chain.mapper.projection.ChainSummaryCountRow;
import git.ggwzz.chain.mapper.projection.EnterpriseCountSummary;
import git.ggwzz.chain.mapper.projection.NodeEnterpriseCountRow;
import git.ggwzz.chain.mapper.projection.NodeSubtreeEnterpriseCountRow;
import git.ggwzz.chain.service.ChainService;
import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import git.ggwzz.chain.vo.ChainOptionVO;
import git.ggwzz.chain.vo.ChainVO;
import git.ggwzz.chain.vo.EnterpriseCountVO;
import git.ggwzz.chain.vo.NodeVO;
import git.ggwzz.chain.vo.TreeNodeVO;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产业链管理服务实现。
 *
 * <p>树形结构用 parent_id + node_path，后端内存组装；
 * 子树查询用 node_path LIKE 前缀（MySQL 5.7 兼容）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainServiceImpl implements ChainService {

    private final ChainMapper chainMapper;
    private final ChainNodeMapper chainNodeMapper;
    private final EnterpriseNodeRelMapper enterpriseNodeRelMapper;
    private final ChainOptionMapper chainOptionMapper;
    private final ChainNodeCandidateMapper chainNodeCandidateMapper;
    private final ChainNodeTreeRules chainNodeTreeRules = new ChainNodeTreeRules();

    // ==================== 产业链 CRUD ====================

    @Override
    public List<ChainOptionVO> listChainOptions() {
        return chainOptionMapper.listOptions();
    }

    @Override
    public List<ChainNodeCandidateVO> listNodeCandidates(Long chainId) {
        if (chainId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产业链ID不能为空");
        }
        return chainNodeCandidateMapper.listByChainId(chainId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChainVO createChain(CreateChainRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        if (request.getChainName() == null
                || request.getChainName().isBlank()) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "产业链名称不能为空");
        }
        Chain chain = new Chain();
        chain.setChainName(request.getChainName().trim());
        chain.setDescription(request.getDescription());
        chain.setDeleted(0);
        if (chainMapper.insert(chain) != 1 || chain.getId() == null) {
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR,
                    "产业链创建失败");
        }
        createDefaultStageNodes(chain.getId());
        ChainVO result = toChainVO(chain);
        result.setNodeCount((long) ChainStageType.values().length);
        return result;
    }

    private void createDefaultStageNodes(Long chainId) {
        if (Set.of(ChainStageType.values()).size() != 3) {
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR,
                    "产业链阶段节点初始化失败");
        }
        for (ChainStageType type : ChainStageType.values()) {
            ChainNode stage = new ChainNode();
            stage.setChainId(chainId);
            stage.setParentId(null);
            stage.setNodeName(type.getDefaultNodeName());
            stage.setNodeLevel(1);
            stage.setNodePath("/");
            stage.setSortOrder(type.getDefaultSortOrder());
            stage.setStageType(type);
            stage.setDeleted(0);
            if (chainNodeMapper.insert(stage) != 1 || stage.getId() == null) {
                throw new BusinessException(
                        ResultCode.INTERNAL_ERROR,
                        "产业链阶段节点初始化失败");
            }
            stage.setNodePath("/" + stage.getId() + "/");
            if (chainNodeMapper.updateById(stage) != 1) {
                throw new BusinessException(
                        ResultCode.INTERNAL_ERROR,
                        "产业链阶段节点初始化失败");
            }
        }
    }

    @Override
    public PageResult<ChainVO> listChains(Integer pageNum, Integer pageSize) {
        Page<Chain> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Chain> wrapper = new LambdaQueryWrapper<Chain>()
                .orderByDesc(Chain::getId);
        chainMapper.selectPage(page, wrapper);
        // 对当前页产业链一次性批量统计节点数和企业数，避免逐条查询形成 N+1。
        List<Long> chainIds = page.getRecords().stream()
                .map(Chain::getId)
                .collect(Collectors.toList());
        Map<Long, ChainSummaryCountRow> summaryCountMap =
                loadChainSummaryCountMap(chainIds);
        List<ChainVO> records = page.getRecords().stream()
                .map(chain -> toChainVO(
                        chain, summaryCountMap.get(chain.getId())))
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ChainVO getChain(Long chainId) {
        Chain chain = chainMapper.selectById(chainId);
        if (chain == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产业链不存在");
        }
        return toChainVOWithCounts(chain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChainVO updateChain(Long chainId, UpdateChainRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        requireChainNameWhenPresent(request.getChainName());
        Chain chain = chainMapper.selectById(chainId);
        if (chain == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产业链不存在");
        }
        if (request.getChainName() != null) {
            chain.setChainName(request.getChainName());
        }
        if (request.getDescription() != null) {
            chain.setDescription(request.getDescription());
        }
        chainMapper.updateById(chain);
        return toChainVOWithCounts(chain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChain(Long chainId) {
        Chain chain = chainMapper.selectById(chainId);
        if (chain == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产业链不存在");
        }
        // 先按产业链批量清理有效挂链关系，避免加载节点 ID 和构造大 IN 集合。
        enterpriseNodeRelMapper.logicalDeleteByChainId(chainId);
        // 逻辑删除全部节点
        chainNodeMapper.delete(new LambdaQueryWrapper<ChainNode>()
                .eq(ChainNode::getChainId, chainId));
        // 逻辑删除产业链
        chainMapper.deleteById(chainId);
    }

    // ==================== 节点树 CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeVO createNode(CreateNodeRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        if (request.getChainId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产业链ID不能为空");
        }
        if (request.getNodeName() == null || request.getNodeName().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "节点名称不能为空");
        }
        requireSortOrderWhenPresent(request.getSortOrder());
        Chain chain = chainMapper.selectById(request.getChainId());
        if (chain == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产业链不存在");
        }
        // 物化路径继承父节点路径，层级由父节点递增；根节点统一从“/”和第 1 层开始。
        ChainNode parent = null;
        int level;
        String parentPath;
        if (request.getParentId() != null) {
            parent = chainNodeMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "父节点不存在");
            }
            // 校验父节点属于目标产业链，避免跨产业链创建导致数据不一致。
            if (!Objects.equals(parent.getChainId(), request.getChainId())) {
                throw new BusinessException(
                        ResultCode.BAD_REQUEST,
                        "父节点不属于当前产业链");
            }
            level = parent.getNodeLevel() + 1;
            parentPath = parent.getNodePath();
        } else {
            level = 1;
            parentPath = "/";
        }
        ChainNode node = new ChainNode();
        node.setChainId(request.getChainId());
        node.setParentId(request.getParentId());
        node.setNodeName(request.getNodeName());
        node.setKeywords(normalizeKeywords(request.getKeywords()));
        node.setNodeLevel(level);
        node.setDescription(request.getDescription());
        node.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        node.setDeleted(0);
        // 先 insert 拿到自增 id，再回填 node_path
        chainNodeMapper.insert(node);
        node.setNodePath(parentPath + node.getId() + "/");
        chainNodeMapper.updateById(node);
        return toNodeVO(node);
    }

    @Override
    public TreeNodeVO getChainTree(Long chainId) {
        Chain chain = chainMapper.selectById(chainId);
        if (chain == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产业链不存在");
        }
        // 一次查询整条产业链的平面节点，再按 parentId 分桶，避免递归查询数据库。
        List<ChainNode> nodes = chainNodeMapper.selectByChainId(chainId);
        // 直接数和子树数均由批量聚合 SQL 返回，组树阶段只负责映射结果。
        Map<Long, Long> directCountMap = new HashMap<>();
        for (NodeEnterpriseCountRow row
                : enterpriseNodeRelMapper.selectDirectCountsByChainId(chainId)) {
            directCountMap.put(row.getNodeId(), row.getDirectEnterpriseCount());
        }
        Map<Long, Long> subtreeCountMap = new HashMap<>();
        for (NodeSubtreeEnterpriseCountRow row
                : enterpriseNodeRelMapper.selectSubtreeCountsByChainId(chainId)) {
            subtreeCountMap.put(
                    row.getNodeId(),
                    row.getSubtreeEnterpriseCount());
        }
        // 将平面节点拆为一级节点和“父节点 ID -> 子节点列表”，供后续递归组树。
        Map<Long, List<ChainNode>> childrenMap = new HashMap<>();
        List<ChainNode> roots = new ArrayList<>();
        for (ChainNode node : nodes) {
            if (node.getParentId() == null) {
                roots.add(node);
            } else {
                childrenMap.computeIfAbsent(node.getParentId(), k -> new ArrayList<>()).add(node);
            }
        }
        TreeNodeVO root = new TreeNodeVO();
        root.setId(chain.getId());
        root.setNodeName(chain.getChainName());
        root.setNodeLevel(0);
        root.setDirectEnterpriseCount(0);
        List<TreeNodeVO> children = buildChildren(
                roots, childrenMap, directCountMap, subtreeCountMap);
        ChainSummaryCountRow chainSummary =
                loadChainSummaryCountMap(List.of(chainId)).get(chainId);
        long rootSubtree = chainSummary == null
                ? 0L
                : chainSummary.getEnterpriseCount();
        root.setSubtreeEnterpriseCount(Math.toIntExact(rootSubtree));
        root.setChildren(children);
        return root;
    }

    private List<TreeNodeVO> buildChildren(
            List<ChainNode> nodes,
            Map<Long, List<ChainNode>> childrenMap,
            Map<Long, Long> directCountMap,
            Map<Long, Long> subtreeCountMap) {
        List<TreeNodeVO> result = new ArrayList<>();
        for (ChainNode node : nodes) {
            TreeNodeVO vo = new TreeNodeVO();
            vo.setId(node.getId());
            vo.setNodeName(node.getNodeName());
            vo.setKeywords(node.getKeywords());
            vo.setDescription(node.getDescription());
            vo.setNodeLevel(node.getNodeLevel());
            vo.setStageType(node.getStageType());
            vo.setParentId(node.getParentId());
            vo.setSortOrder(node.getSortOrder());
            List<TreeNodeVO> children = buildChildren(
                    childrenMap.getOrDefault(
                            node.getId(), Collections.emptyList()),
                    childrenMap,
                    directCountMap,
                    subtreeCountMap);
            long directCount = directCountMap.getOrDefault(node.getId(), 0L);
            long subtreeCount = subtreeCountMap.getOrDefault(node.getId(), 0L);
            vo.setDirectEnterpriseCount(Math.toIntExact(directCount));
            vo.setSubtreeEnterpriseCount(Math.toIntExact(subtreeCount));
            vo.setChildren(children);
            result.add(vo);
        }
        return result;
    }

    @Override
    public PageResult<NodeVO> listNodes(Long chainId, Long parentId, Integer pageNum, Integer pageSize) {
        Page<ChainNode> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ChainNode> wrapper = new LambdaQueryWrapper<ChainNode>()
                .eq(ChainNode::getChainId, chainId)
                .orderByAsc(ChainNode::getSortOrder)
                .orderByAsc(ChainNode::getId);
        if (parentId != null) {
            wrapper.eq(ChainNode::getParentId, parentId);
        }
        chainNodeMapper.selectPage(page, wrapper);
        List<NodeVO> records = page.getRecords().stream().map(this::toNodeVO).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeVO updateNode(Long nodeId, UpdateNodeRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        requireNodeId(nodeId);
        requireNodeNameWhenPresent(request.getNodeName());
        requireSortOrderWhenPresent(request.getSortOrder());
        ChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节点不存在");
        }
        if (request.isParentChangeRequested()) {
            moveNodeInternal(
                    node,
                    request.getParentId(),
                    request.getSortOrder(),
                    "阶段节点无法修改父节点");
        }
        if (request.getNodeName() != null) {
            node.setNodeName(request.getNodeName());
        }
        if (request.getKeywords() != null) {
            node.setKeywords(normalizeKeywords(request.getKeywords()));
        }
        if (request.getDescription() != null) {
            node.setDescription(request.getDescription());
        }
        if (request.getSortOrder() != null) {
            node.setSortOrder(request.getSortOrder());
        }
        if (chainNodeMapper.updateById(node) != 1) {
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR,
                    "节点更新失败");
        }
        return toNodeVO(node);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeVO moveNode(Long nodeId, MoveNodeRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        requireNodeId(nodeId);
        ChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节点不存在");
        }
        moveNodeInternal(
                node,
                request.getNewParentId(),
                request.getSortOrder(),
                "阶段节点无法修改父节点");
        return toNodeVO(node);
    }

    /**
     * 节点子树移动内部方法，供 {@link #moveNode} 和 {@link #updateNode} 复用。
     *
     * <p>校验阶段节点保护、自移动、跨产业链和环约束，然后一条 SQL 批量改写子树路径和层级。
     * 移动失败时抛出异常，不更新任何属性。</p>
     *
     * @param node             已加载的待移动节点
     * @param newParentId      目标父节点 ID；null 表示移动为一级节点
     * @param sortOrder        新的同级排序；null 表示不更新排序
     * @param stageErrorMessage 阶段节点保护异常消息
     */
    private void moveNodeInternal(
            ChainNode node,
            Long newParentId,
            Integer sortOrder,
            String stageErrorMessage) {
        ChainNodeTreeRules.MovePlan plan = chainNodeTreeRules.planMove(
                node,
                newParentId,
                sortOrder,
                chainNodeMapper::selectById,
                stageErrorMessage);
        int affected = chainNodeMapper.updateSubtree(
                plan.chainId(),
                plan.nodeId(),
                plan.newParentId(),
                plan.sortOrder(),
                plan.oldPath(),
                plan.oldPathPattern(),
                plan.newPath(),
                plan.levelOffset());
        if (affected == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节点不存在");
        }

        plan.applyTo(node);
    }

    private void requireNodeId(Long nodeId) {
        if (nodeId == null) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "节点ID不能为空");
        }
    }

    private void requireNodeNameWhenPresent(String nodeName) {
        if (nodeName != null && nodeName.isBlank()) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "节点名称不能为空");
        }
    }

    private void requireChainNameWhenPresent(String chainName) {
        if (chainName != null && chainName.isBlank()) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "产业链名称不能为空");
        }
    }

    private void requireSortOrderWhenPresent(Integer sortOrder) {
        if (sortOrder != null && sortOrder < 0) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "排序值不能小于 0");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortNodes(SortNodesRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        if (request.getNodeOrders() == null
                || request.getNodeOrders().isEmpty()
                || request.getNodeOrders().stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "排序节点不能为空");
        }
        // 每项更新都附带 parentId 条件，只允许调整同一父节点下的同级顺序。
        for (SortNodeOrderRequest order : request.getNodeOrders()) {
            ChainNode update = new ChainNode();
            update.setSortOrder(order.getSortOrder());
            LambdaUpdateWrapper<ChainNode> wrapper = new LambdaUpdateWrapper<ChainNode>()
                    .eq(ChainNode::getId, order.getNodeId());
            if (request.getParentId() == null) {
                wrapper.isNull(ChainNode::getParentId);
            } else {
                wrapper.eq(ChainNode::getParentId, request.getParentId());
            }
            int affected = chainNodeMapper.update(update, wrapper);
            if (affected != 1) {
                throw new BusinessException(
                        ResultCode.NOT_FOUND,
                        "排序节点不存在或不属于指定父节点");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long nodeId) {
        requireNodeId(nodeId);
        ChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节点不存在");
        }
        chainNodeTreeRules.requireOrdinaryNode(node, "上下游阶段节点不允许删除");
        if (chainNodeMapper.countChildren(nodeId) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该节点存在子节点，无法删除");
        }
        // 清理该节点的挂链关系（逻辑删除）
        enterpriseNodeRelMapper.delete(new LambdaQueryWrapper<EnterpriseNodeRel>()
                .eq(EnterpriseNodeRel::getNodeId, nodeId));
        chainNodeMapper.deleteById(nodeId);
    }

    @Override
    public PageResult<NodeVO> searchNodes(String keyword, Integer pageNum, Integer pageSize) {
        Page<ChainNode> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ChainNode> wrapper = new LambdaQueryWrapper<ChainNode>()
                .likeRight(ChainNode::getNodeName, keyword)
                .orderByAsc(ChainNode::getChainId)
                .orderByAsc(ChainNode::getSortOrder);
        chainNodeMapper.selectPage(page, wrapper);
        List<NodeVO> records = page.getRecords().stream().map(this::toNodeVO).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<NodeVO> searchByKeywords(
            String keyword, Long chainId, int pageNum, int pageSize) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "搜索关键词不能为空");
        }
        Page<ChainNode> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ChainNode> wrapper = new LambdaQueryWrapper<ChainNode>()
                .like(ChainNode::getKeywords, keyword);
        if (chainId != null) {
            wrapper.eq(ChainNode::getChainId, chainId);
        }
        wrapper.orderByAsc(ChainNode::getNodeLevel)
                .orderByAsc(ChainNode::getSortOrder)
                .orderByAsc(ChainNode::getId);
        chainNodeMapper.selectPage(page, wrapper);
        List<NodeVO> records = page.getRecords().stream()
                .map(this::toNodeVO)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ==================== 企业数量统计 ====================

    @Override
    public EnterpriseCountVO getEnterpriseCount(Long nodeId) {
        ChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节点不存在");
        }
        // 聚合 SQL 同时返回当前节点直接挂载数和按 node_path 前缀统计的子树总数。
        EnterpriseCountSummary summary =
                enterpriseNodeRelMapper.selectEnterpriseCountSummary(
                        node.getChainId(),
                        nodeId,
                        node.getNodePath() + "%");
        EnterpriseCountVO vo = new EnterpriseCountVO();
        vo.setNodeId(nodeId);
        vo.setNodeName(node.getNodeName());
        vo.setDirectEnterpriseCount(
                Math.toIntExact(summary.getDirectEnterpriseCount()));
        vo.setTotalEnterpriseCount(
                Math.toIntExact(summary.getTotalEnterpriseCount()));
        return vo;
    }

    // ==================== VO 转换 ====================

    /**
     * 清洗节点关键词：按逗号切分、去前后空白、剔除空项、去重后重新拼接。
     *
     * <p>输入为 null 或全空白时返回 null，避免落库空串或纯逗号串。
     * 例：{@code " 电芯 , 原材料,, 正极材料, 电芯 "} → {@code "电芯,原材料,正极材料"}。</p>
     *
     * @param keywords 原始关键词串
     * @return 清洗后的关键词串，可能为 null
     */
    private String normalizeKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return null;
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private Map<Long, ChainSummaryCountRow> loadChainSummaryCountMap(
            List<Long> chainIds) {
        if (chainIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return chainMapper.selectSummaryCountsByChainIds(chainIds).stream()
                .collect(Collectors.toMap(
                        ChainSummaryCountRow::getChainId,
                        row -> row));
    }

    private ChainVO toChainVO(Chain chain) {
        ChainVO vo = new ChainVO();
        vo.setId(chain.getId());
        vo.setChainName(chain.getChainName());
        vo.setDescription(chain.getDescription());
        vo.setNodeCount(0L);
        vo.setEnterpriseCount(0L);
        vo.setCreateTime(chain.getCreateTime());
        return vo;
    }

    private ChainVO toChainVO(Chain chain, ChainSummaryCountRow counts) {
        ChainVO vo = toChainVO(chain);
        if (counts != null) {
            vo.setNodeCount(counts.getNodeCount());
            vo.setEnterpriseCount(counts.getEnterpriseCount());
        }
        return vo;
    }

    private ChainVO toChainVOWithCounts(Chain chain) {
        Map<Long, ChainSummaryCountRow> summaryCountMap =
                loadChainSummaryCountMap(List.of(chain.getId()));
        return toChainVO(chain, summaryCountMap.get(chain.getId()));
    }

    private NodeVO toNodeVO(ChainNode node) {
        NodeVO vo = new NodeVO();
        vo.setId(node.getId());
        vo.setChainId(node.getChainId());
        vo.setParentId(node.getParentId());
        vo.setNodeName(node.getNodeName());
        vo.setKeywords(node.getKeywords());
        vo.setDescription(node.getDescription());
        vo.setNodeLevel(node.getNodeLevel());
        vo.setNodePath(node.getNodePath());
        vo.setSortOrder(node.getSortOrder());
        vo.setStageType(node.getStageType());
        vo.setCreateTime(node.getCreateTime());
        return vo;
    }
}
