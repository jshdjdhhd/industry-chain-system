package git.ggwzz.chain.controller;

import git.ggwzz.chain.dto.BatchLinkEnterpriseRequest;
import git.ggwzz.chain.dto.LinkEnterpriseRequest;
import git.ggwzz.chain.service.EnterpriseNodeRelService;
import git.ggwzz.chain.vo.BatchLinkEnterpriseVO;
import git.ggwzz.chain.vo.NodeEnterpriseVO;
import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.security.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产业链节点企业挂链接口。
 *
 * <p>提供挂链企业到节点、解绑企业、查询节点下企业列表能力。
 * 挂链/解绑操作记录操作日志。</p>
 */
@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class ChainNodeController {

    private final EnterpriseNodeRelService enterpriseNodeRelService;

    /**
     * 挂链企业到节点。
     *
     * @param id      节点ID
     * @param request 挂链请求（企业ID）
     * @return 成功响应
     */
    @PostMapping("/{id}/enterprises")
    @PreAuthorize("hasAuthority('chain:node:link-enterprise')")
    @OperationLog(action = "bind", targetType = "enterprise_node_rel",
            targetId = "#id", detail = "挂链企业到节点")
    public Result<Void> linkEnterprise(@PathVariable Long id,
                                       @Valid @RequestBody LinkEnterpriseRequest request) {
        Long operator = SecurityUtils.currentUserId();
        enterpriseNodeRelService.linkManually(request.getEnterpriseId(), id, "人工挂链", operator);
        return Result.success();
    }

    /**
     * 批量挂链企业到节点。
     *
     * @param id      节点ID
     * @param request 批量挂链请求
     * @return 批量处理结果
     */
    @PostMapping("/{id}/enterprises/batch")
    @PreAuthorize("hasAuthority('chain:node:batch-link-enterprise')")
    @OperationLog(action = "batch_bind", targetType = "enterprise_node_rel",
            targetId = "#id", detail = "批量挂接企业到节点")
    public Result<BatchLinkEnterpriseVO> batchLinkEnterprises(
            @PathVariable Long id,
            @Valid @RequestBody BatchLinkEnterpriseRequest request) {
        Long operator = SecurityUtils.currentUserId();
        BatchLinkEnterpriseVO result = enterpriseNodeRelService.linkManuallyBatch(
                id, request.getEnterpriseIds(), operator);
        return Result.success(result);
    }

    /**
     * 解绑企业。
     *
     * @param id           节点ID
     * @param enterpriseId 企业ID
     * @return 成功响应
     */
    @DeleteMapping("/{id}/enterprises/{enterpriseId}")
    @PreAuthorize("hasAuthority('chain:node:unlink-enterprise')")
    @OperationLog(action = "unbind", targetType = "enterprise_node_rel",
            targetId = "#enterpriseId", detail = "从节点解绑企业")
    public Result<Void> unlinkEnterprise(@PathVariable Long id,
                                         @PathVariable Long enterpriseId) {
        Long operator = SecurityUtils.currentUserId();
        enterpriseNodeRelService.unlink(id, enterpriseId, operator);
        return Result.success();
    }

    /**
     * 查询节点下的企业列表。
     *
     * @param id 节点ID
     * @return 包含企业名称的节点企业列表
     */
    @GetMapping("/{id}/enterprises")
    @PreAuthorize("hasAuthority('chain:node:query')")
    public Result<List<NodeEnterpriseVO>> listEnterprises(@PathVariable Long id) {
        List<NodeEnterpriseVO> list = enterpriseNodeRelService.listByNodeId(id);
        return Result.success(list);
    }
}
