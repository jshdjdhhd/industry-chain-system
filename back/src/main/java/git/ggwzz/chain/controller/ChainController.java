package git.ggwzz.chain.controller;

import git.ggwzz.chain.dto.CreateChainRequest;
import git.ggwzz.chain.dto.CreateNodeRequest;
import git.ggwzz.chain.dto.MoveNodeRequest;
import git.ggwzz.chain.dto.SortNodesRequest;
import git.ggwzz.chain.dto.UpdateChainRequest;
import git.ggwzz.chain.dto.UpdateNodeRequest;
import git.ggwzz.chain.service.ChainService;
import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import git.ggwzz.chain.vo.ChainOptionVO;
import git.ggwzz.chain.vo.ChainVO;
import git.ggwzz.chain.vo.EnterpriseCountVO;
import git.ggwzz.chain.vo.NodeVO;
import git.ggwzz.chain.vo.TreeNodeVO;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产业链管理接口。
 *
 * <p>提供产业链 CRUD、节点树 CRUD、企业数量统计。
 * 挂链/解绑见 {@code ChainNodeController}（/api/nodes），不在本控制器重复。</p>
 */
@RestController
@RequestMapping("/api/chains")
@RequiredArgsConstructor
@Validated
public class ChainController {

    private final ChainService chainService;

    // ==================== 产业链 ====================

    @PostMapping
    @PreAuthorize("hasAuthority('chain:chain:create')")
    @OperationLog(action = "create_chain", targetType = "chain", detail = "创建产业链")
    public Result<ChainVO> createChain(@Valid @RequestBody CreateChainRequest request) {
        return Result.success(chainService.createChain(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('chain:chain:query')")
    public Result<PageResult<ChainVO>> listChains(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(chainService.listChains(pageNum, pageSize));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('chain:chain:query')")
    public Result<List<ChainOptionVO>> listChainOptions() {
        return Result.success(chainService.listChainOptions());
    }

    @GetMapping("/{chainId}")
    @PreAuthorize("hasAuthority('chain:chain:query')")
    public Result<ChainVO> getChain(@PathVariable Long chainId) {
        return Result.success(chainService.getChain(chainId));
    }

    @PutMapping("/{chainId}")
    @PreAuthorize("hasAuthority('chain:chain:update')")
    @OperationLog(action = "update_chain", targetType = "chain",
            targetId = "#chainId", detail = "更新产业链")
    public Result<ChainVO> updateChain(@PathVariable Long chainId,
                                       @Valid @RequestBody UpdateChainRequest request) {
        return Result.success(chainService.updateChain(chainId, request));
    }

    @DeleteMapping("/{chainId}")
    @PreAuthorize("hasAuthority('chain:chain:delete')")
    @OperationLog(action = "delete_chain", targetType = "chain",
            targetId = "#chainId", detail = "删除产业链")
    public Result<Void> deleteChain(@PathVariable Long chainId) {
        chainService.deleteChain(chainId);
        return Result.success();
    }

    // ==================== 节点 ====================

    @PostMapping("/{chainId}/nodes")
    @PreAuthorize("hasAuthority('chain:node:create')")
    @OperationLog(action = "create_node", targetType = "chain_node",
            targetId = "#chainId", detail = "创建链节点")
    public Result<NodeVO> createNode(@PathVariable Long chainId,
                                     @Valid @RequestBody CreateNodeRequest request) {
        // URL 路径参数是创建节点时唯一权威的产业链 ID，无条件覆盖请求体中的同名字段。
        request.setChainId(chainId);
        return Result.success(chainService.createNode(request));
    }

    @GetMapping("/{chainId}/tree")
    @PreAuthorize("hasAuthority('chain:node:query')")
    public Result<TreeNodeVO> getChainTree(@PathVariable Long chainId) {
        return Result.success(chainService.getChainTree(chainId));
    }

    @GetMapping("/{chainId}/node-options")
    @PreAuthorize("hasAuthority('chain:node:query')")
    public Result<List<ChainNodeCandidateVO>> listNodeCandidates(
            @PathVariable Long chainId) {
        return Result.success(chainService.listNodeCandidates(chainId));
    }

    @GetMapping("/{chainId}/nodes")
    @PreAuthorize("hasAuthority('chain:node:query')")
    public Result<PageResult<NodeVO>> listNodes(@PathVariable Long chainId,
                                                @RequestParam(required = false) Long parentId,
                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(chainService.listNodes(chainId, parentId, pageNum, pageSize));
    }

    @PutMapping("/nodes/{nodeId}")
    @PreAuthorize(
            "hasAuthority('chain:node:update')"
                    + " and (!#request.parentChangeRequested"
                    + " or hasAuthority('chain:node:move'))")
    @OperationLog(action = "update_node", targetType = "chain_node",
            targetId = "#nodeId", detail = "更新链节点")
    public Result<NodeVO> updateNode(@PathVariable Long nodeId,
                                     @Valid @RequestBody UpdateNodeRequest request) {
        return Result.success(chainService.updateNode(nodeId, request));
    }

    @PutMapping("/nodes/{nodeId}/move")
    @PreAuthorize("hasAuthority('chain:node:move')")
    @OperationLog(action = "move_node", targetType = "chain_node",
            targetId = "#nodeId", detail = "移动链节点")
    public Result<NodeVO> moveNode(@PathVariable Long nodeId,
                                   @Valid @RequestBody MoveNodeRequest request) {
        return Result.success(chainService.moveNode(nodeId, request));
    }

    @PutMapping("/nodes/sort")
    @PreAuthorize("hasAuthority('chain:node:sort')")
    @OperationLog(action = "sort_nodes", targetType = "chain_node", detail = "更新链节点排序")
    public Result<Void> sortNodes(@Valid @RequestBody SortNodesRequest request) {
        chainService.sortNodes(request);
        return Result.success();
    }

    @DeleteMapping("/nodes/{nodeId}")
    @PreAuthorize("hasAuthority('chain:node:delete')")
    @OperationLog(action = "delete_node", targetType = "chain_node",
            targetId = "#nodeId", detail = "删除链节点")
    public Result<Void> deleteNode(@PathVariable Long nodeId) {
        chainService.deleteNode(nodeId);
        return Result.success();
    }

    @GetMapping("/nodes/search")
    @PreAuthorize("hasAuthority('chain:node:query')")
    public Result<PageResult<NodeVO>> searchNodes(@RequestParam String keyword,
                                                  @RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(chainService.searchNodes(keyword, pageNum, pageSize));
    }

    /**
     * 按节点知识库关键词搜索节点（全模糊匹配 {@code keywords} 字段）。
     *
     * <p>与 {@code /nodes/search}（按节点名称右模糊）区分：本接口面向"节点关键词词库"检索。
     * {@code chainId} 不传时跨产业链搜索。</p>
     *
     * @param keyword  搜索关键词，不能为空白
     * @param chainId  产业链 ID，可选，不传则跨产业链
     * @param pageNum  页码，从 1 开始，默认 1
     * @param pageSize 每页大小，默认 20
     * @return 命中节点分页
     */
    @GetMapping("/nodes/search-by-keyword")
    @PreAuthorize("hasAuthority('chain:node:query')")
    public Result<PageResult<NodeVO>> searchByKeyword(
            @RequestParam @NotBlank(message = "搜索关键词不能为空") String keyword,
            @RequestParam(required = false) Long chainId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(chainService.searchByKeywords(keyword, chainId, pageNum, pageSize));
    }

    @GetMapping("/nodes/{nodeId}/enterprise-count")
    @PreAuthorize("hasAuthority('chain:node:query')")
    public Result<EnterpriseCountVO> getEnterpriseCount(@PathVariable Long nodeId) {
        return Result.success(chainService.getEnterpriseCount(nodeId));
    }
}
