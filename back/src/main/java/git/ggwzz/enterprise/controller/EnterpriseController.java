package git.ggwzz.enterprise.controller;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.enterprise.dto.*;
import git.ggwzz.enterprise.service.EnterpriseProfileAnalysisService;
import git.ggwzz.enterprise.service.EnterpriseService;
import git.ggwzz.enterprise.service.ImportService;
import git.ggwzz.enterprise.vo.EnterpriseDetailVO;
import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import git.ggwzz.enterprise.vo.EnterpriseProfileAnalysisVO;
import git.ggwzz.enterprise.vo.EnterpriseRadarVO;
import git.ggwzz.enterprise.vo.EnterpriseVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * 企业管理接口。
 *
 * <p>提供企业 CRUD、入库/出库状态流转、标签绑定/解绑。</p>
 */
@RestController
@RequestMapping("/api/enterprises")
@RequiredArgsConstructor
public class EnterpriseController {

    private final EnterpriseService enterpriseService;
    private final ImportService importService;
    private final EnterpriseProfileAnalysisService enterpriseProfileAnalysisService;

    /**
     * 企业分页列表。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('enterprise:enterprise:query')")
    public Result<PageResult<EnterpriseVO>> list(EnterpriseQueryRequest query) {
        return Result.success(enterpriseService.list(query));
    }

    /**
     * 企业下拉选项（仅 ACTIVE 企业的 ID 与名称）。
     *
     * @param keyword 企业名称前缀，可选
     * @param limit   最大返回条数，默认 20，最大 100
     * @return 企业选项列表
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('enterprise:enterprise:query')")
    public Result<List<EnterpriseOptionVO>> options(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(enterpriseService.listOptions(keyword, limit));
    }

    /**
     * 企业详情（含标签和挂链节点）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('enterprise:enterprise:query')")
    public Result<EnterpriseDetailVO> getById(@PathVariable Long id) {
        return Result.success(enterpriseService.getById(id));
    }

    /**
     * 企业画像分析（雷达指标与相似企业）。
     */
    @GetMapping("/{id}/profile-analysis")
    @PreAuthorize("hasAuthority('enterprise:enterprise:query')")
    public Result<EnterpriseProfileAnalysisVO> profileAnalysis(
            @PathVariable Long id,
            @RequestParam(required = false) Integer similarLimit) {
        return Result.success(enterpriseProfileAnalysisService.analyze(id, similarLimit));
    }

    /**
     * 企业雷达画像（仅雷达指标，不含相似企业，用于前端首屏快速加载）。
     */
    @GetMapping("/{id}/profile-analysis/radar")
    @PreAuthorize("hasAuthority('enterprise:enterprise:query')")
    public Result<EnterpriseRadarVO> radar(@PathVariable Long id) {
        return Result.success(enterpriseProfileAnalysisService.analyzeRadar(id));
    }

    /**
     * 企业画像向量预热。
     */
    @PostMapping("/profile-analysis/vector-warmup")
    @PreAuthorize("hasAuthority('enterprise:enterprise:update')")
    public Result<VectorWarmupResultVO> warmupProfileVectors(
            @RequestBody(required = false) VectorWarmupRequest request) {
        return Result.success(enterpriseProfileAnalysisService.warmupVectors(request));
    }

    /**
     * 新增企业（默认未入库）。
     */
    @PostMapping
    @OperationLog(action = "create_enterprise", targetType = "enterprise", detail = "新增企业")
    @PreAuthorize("hasAuthority('enterprise:enterprise:create')")
    public Result<EnterpriseDetailVO> create(@Valid @RequestBody CreateEnterpriseRequest request) {
        return Result.success(enterpriseService.create(request));
    }

    /**
     * 编辑企业。
     */
    @PutMapping("/{id}")
    @OperationLog(action = "update_enterprise", targetType = "enterprise",
            targetId = "#id", detail = "编辑企业")
    @PreAuthorize("hasAuthority('enterprise:enterprise:update')")
    public Result<EnterpriseDetailVO> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateEnterpriseRequest request) {
        return Result.success(enterpriseService.update(id, request));
    }

    /**
     * 删除企业（仅未入库可删）。
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete_enterprise", targetType = "enterprise",
            targetId = "#id", detail = "删除企业")
    @PreAuthorize("hasAuthority('enterprise:enterprise:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        enterpriseService.delete(id);
        return Result.success();
    }

    /**
     * 企业入库（UNREGISTERED/OUTBOUND → ACTIVE）。
     */
    @PostMapping("/{id}/inbound")
    @OperationLog(action = "inbound_enterprise", targetType = "enterprise",
            targetId = "#id", detail = "企业入库")
    @PreAuthorize("hasAuthority('enterprise:enterprise:inbound')")
    public Result<Void> inbound(@PathVariable Long id) {
        enterpriseService.inbound(id);
        return Result.success();
    }

    /**
     * 企业出库（ACTIVE → OUTBOUND）。
     */
    @PostMapping("/{id}/outbound")
    @OperationLog(action = "outbound_enterprise", targetType = "enterprise",
            targetId = "#id", detail = "企业出库")
    @PreAuthorize("hasAuthority('enterprise:enterprise:outbound')")
    public Result<Void> outbound(@PathVariable Long id) {
        enterpriseService.outbound(id);
        return Result.success();
    }

    /**
     * 批量入库（处理 UNREGISTERED 与 OUTBOUND 状态的企业）。
     */
    @PostMapping("/batch-inbound")
    @OperationLog(action = "batch_inbound_enterprise", targetType = "enterprise", detail = "批量入库")
    @PreAuthorize("hasAuthority('enterprise:enterprise:inbound')")
    public Result<Integer> batchInbound(@RequestBody Map<String, List<Long>> body) {
        int successCount = enterpriseService.batchInbound(body.get("ids"));
        return Result.success(successCount);
    }

    /**
     * 批量出库。
     */
    @PostMapping("/batch-outbound")
    @OperationLog(action = "batch_outbound_enterprise", targetType = "enterprise", detail = "批量出库")
    @PreAuthorize("hasAuthority('enterprise:enterprise:batch-outbound')")
    public Result<Void> batchOutbound(@RequestBody Map<String, List<Long>> body) {
        enterpriseService.batchOutbound(body.get("ids"));
        return Result.success();
    }

    /**
     * 为企业绑定标签。
     */
    @PostMapping("/{id}/tags")
    @OperationLog(action = "bind_tag", targetType = "enterprise",
            targetId = "#id", detail = "绑定标签")
    @PreAuthorize("hasAuthority('enterprise:tag:bind')")
    public Result<Void> bindTag(@PathVariable Long id, @Valid @RequestBody BindTagRequest request) {
        enterpriseService.bindTag(id, request.getTagId());
        return Result.success();
    }

    /**
     * 为企业解绑标签。
     */
    @DeleteMapping("/{id}/tags/{tagId}")
    @OperationLog(action = "unbind_tag", targetType = "enterprise",
            targetId = "#id", detail = "解绑标签")
    @PreAuthorize("hasAuthority('enterprise:tag:unbind')")
    public Result<Void> unbindTag(@PathVariable Long id, @PathVariable Long tagId) {
        enterpriseService.unbindTag(id, tagId);
        return Result.success();
    }

    /**
     * 批量导入企业（Excel/CSV）。
     */
    @PostMapping("/import")
    @OperationLog(action = "import_enterprises", targetType = "enterprise", detail = "批量导入企业")
    @PreAuthorize("hasAuthority('enterprise:enterprise:import')")
    public Result<ImportResultVO> importEnterprises(@RequestParam("file") MultipartFile file, Principal principal) {
        String operator = principal != null ? principal.getName() : "unknown";
        return Result.success(importService.importEnterprises(file, operator));
    }

    /**
     * 查询导入记录（分页）。
     */
    @GetMapping("/import-records")
    @PreAuthorize("hasAuthority('enterprise:enterprise:query')")
    public Result<PageResult<ImportRecordVO>> importRecords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(importService.listImportRecords(keyword, status, pageNum, pageSize));
    }
}
