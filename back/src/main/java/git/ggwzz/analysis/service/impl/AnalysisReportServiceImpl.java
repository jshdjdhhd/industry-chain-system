package git.ggwzz.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.analysis.dto.AnalysisReportQuery;
import git.ggwzz.analysis.dto.CreateAnalysisReportRequest;
import git.ggwzz.analysis.dto.ReportDownloadPayload;
import git.ggwzz.analysis.entity.AnalysisReportContent;
import git.ggwzz.analysis.entity.AnalysisReportTask;
import git.ggwzz.analysis.enums.AnalysisReportStatus;
import git.ggwzz.analysis.enums.AnalysisReportType;
import git.ggwzz.analysis.enums.AnalysisScopeType;
import git.ggwzz.analysis.mapper.AnalysisReportContentMapper;
import git.ggwzz.analysis.mapper.AnalysisReportTaskMapper;
import git.ggwzz.analysis.service.AnalysisReportService;
import git.ggwzz.analysis.service.AnalysisReportWorker;
import git.ggwzz.analysis.vo.AnalysisReportDetailVO;
import git.ggwzz.analysis.vo.AnalysisReportTaskVO;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 健康分析报告服务实现。
 *
 * <p>负责参数校验、范围展开、任务创建与状态流转；异步正文生成委托给
 * {@link AnalysisReportWorker}。范围展开复用 chain 节点的 node_path 前缀匹配。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisReportServiceImpl implements AnalysisReportService {

    private final AnalysisReportTaskMapper analysisReportTaskMapper;
    private final AnalysisReportContentMapper analysisReportContentMapper;
    private final AnalysisReportWorker analysisReportWorker;
    private final ChainMapper chainMapper;
    private final ChainNodeMapper chainNodeMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnalysisReportTaskVO createReport(CreateAnalysisReportRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        // 校验产业链存在
        Chain chain = chainMapper.selectById(request.getChainId());
        if (chain == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产业链不存在");
        }
        // 校验报告类型
        AnalysisReportType reportType = parseReportType(request.getReportType());
        // 校验范围类型
        AnalysisScopeType scopeType = parseScopeType(request.getScopeType());
        // 校验 nodeIds
        List<Long> selectedNodeIds = request.getNodeIds();
        if (scopeType == AnalysisScopeType.SELECTED_NODES) {
            if (selectedNodeIds == null || selectedNodeIds.isEmpty()) {
                throw new BusinessException(
                        ResultCode.BAD_REQUEST,
                        "SELECTED_NODES 范围下 nodeIds 不能为空");
            }
        }
        // 校验 nodeIds 属于同一链
        List<ChainNode> chainNodes = chainNodeMapper.selectByChainId(request.getChainId());
        Map<Long, ChainNode> nodeMap = chainNodes.stream()
                .collect(Collectors.toMap(ChainNode::getId, n -> n, (a, b) -> a));
        if (selectedNodeIds != null && !selectedNodeIds.isEmpty()) {
            for (Long nodeId : selectedNodeIds) {
                ChainNode node = nodeMap.get(nodeId);
                if (node == null) {
                    throw new BusinessException(
                            ResultCode.BAD_REQUEST,
                            "节点 " + nodeId + " 不属于产业链 " + request.getChainId());
                }
            }
        }
        // 展开子树
        List<Long> resolvedNodeIds = resolveScope(scopeType, selectedNodeIds, chainNodes);

        // 组装 input_snapshot
        String inputSnapshot = buildInputSnapshot(request, chain, resolvedNodeIds.size());

        AnalysisReportTask task = new AnalysisReportTask();
        task.setChainId(request.getChainId());
        task.setReportType(reportType.name());
        task.setScopeType(scopeType.name());
        task.setSelectedNodeIds(selectedNodeIds);
        task.setResolvedNodeIds(resolvedNodeIds);
        task.setStatus(AnalysisReportStatus.PROCESSING.name());
        task.setRetryFromTaskId(null);
        task.setRetryCount(0);
        task.setInputSnapshot(inputSnapshot);
        task.setCreatedBy(currentUsernameSafe());
        analysisReportTaskMapper.insert(task);

        // 触发异步执行：必须在事务提交后再投递，否则 Worker 可能在 task insert
        // 提交前就去 selectById，查不到记录而走 warn-return 分支，任务永远卡 PROCESSING。
        scheduleWorkerAfterCommit(task.getId());

        return toTaskVO(task, chain.getChainName());
    }

    @Override
    public PageResult<AnalysisReportTaskVO> listReports(AnalysisReportQuery query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1
                ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 10 : Math.min(query.getPageSize(), 100);
        Page<AnalysisReportTask> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AnalysisReportTask> wrapper =
                new LambdaQueryWrapper<AnalysisReportTask>()
                        .orderByDesc(AnalysisReportTask::getId);
        if (query.getChainId() != null) {
            wrapper.eq(AnalysisReportTask::getChainId, query.getChainId());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(AnalysisReportTask::getStatus, query.getStatus());
        }
        analysisReportTaskMapper.selectPage(page, wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(),
                    page.getTotal(), page.getCurrent(), page.getSize());
        }
        Set<Long> chainIds = page.getRecords().stream()
                .map(AnalysisReportTask::getChainId)
                .collect(Collectors.toSet());
        Map<Long, String> chainNameMap = loadChainNameMap(chainIds);
        List<AnalysisReportTaskVO> records = page.getRecords().stream()
                .map(t -> toTaskVO(t, chainNameMap.get(t.getChainId())))
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AnalysisReportDetailVO getReport(Long reportId) {
        AnalysisReportTask task = analysisReportTaskMapper.selectById(reportId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告任务不存在");
        }
        AnalysisReportContent content = analysisReportContentMapper.selectOne(
                new LambdaQueryWrapper<AnalysisReportContent>()
                        .eq(AnalysisReportContent::getTaskId, reportId));
        Chain chain = chainMapper.selectById(task.getChainId());
        return toDetailVO(task, content, chain == null ? null : chain.getChainName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnalysisReportTaskVO retryReport(Long reportId) {
        AnalysisReportTask origin = analysisReportTaskMapper.selectById(reportId);
        if (origin == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告任务不存在");
        }
        AnalysisReportStatus status = AnalysisReportStatus.from(origin.getStatus());
        if (status == null || !status.isRetryable()) {
            throw new BusinessException(
                    ResultCode.CONFLICT,
                    "仅 FAILED、CANCELLED 状态的任务可重试，当前状态："
                            + origin.getStatus());
        }
        Chain chain = chainMapper.selectById(origin.getChainId());
        if (chain == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产业链不存在");
        }
        AnalysisReportTask retry = new AnalysisReportTask();
        retry.setChainId(origin.getChainId());
        retry.setReportType(origin.getReportType());
        retry.setScopeType(origin.getScopeType());
        retry.setSelectedNodeIds(origin.getSelectedNodeIds());
        retry.setResolvedNodeIds(origin.getResolvedNodeIds());
        retry.setStatus(AnalysisReportStatus.PROCESSING.name());
        retry.setRetryFromTaskId(reportId);
        retry.setRetryCount((origin.getRetryCount() == null ? 0 : origin.getRetryCount()) + 1);
        retry.setInputSnapshot(origin.getInputSnapshot());
        retry.setCreatedBy(currentUsernameSafe());
        analysisReportTaskMapper.insert(retry);

        // 与 createReport 一致：事务提交后再投递异步 Worker，避免时序竞态。
        scheduleWorkerAfterCommit(retry.getId());

        return toTaskVO(retry, chain.getChainName());
    }

    @Override
    public ReportDownloadPayload loadDownloadPayload(Long reportId, String format) {
        if (format == null || format.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "下载格式不能为空");
        }
        String fmt = format.trim().toLowerCase(Locale.ROOT);
        if (!"pdf".equals(fmt) && !"md".equals(fmt)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "不支持的下载格式：" + format + "（仅支持 pdf、md）");
        }
        AnalysisReportTask task = analysisReportTaskMapper.selectById(reportId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告任务不存在");
        }
        AnalysisReportStatus status = AnalysisReportStatus.from(task.getStatus());
        if (status != AnalysisReportStatus.COMPLETED) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "仅 COMPLETED 状态的报告可下载，当前状态：" + task.getStatus());
        }
        AnalysisReportContent content = analysisReportContentMapper.selectOne(
                new LambdaQueryWrapper<AnalysisReportContent>()
                        .eq(AnalysisReportContent::getTaskId, reportId));
        if (content == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告内容不存在");
        }
        String fileBase = sanitizeFileName(content.getTitle());
        if (fileBase == null || fileBase.isBlank()) {
            fileBase = "report-" + reportId;
        }
        if ("pdf".equals(fmt)) {
            String path = content.getPdfFilePath();
            if (path == null || path.isBlank()) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "该报告未生成 PDF（可能因运行环境缺少中文字体降级），请改用 md 格式下载");
            }
            java.nio.file.Path file = java.nio.file.Paths.get(path);
            if (!java.nio.file.Files.exists(file) || !java.nio.file.Files.isReadable(file)) {
                throw new BusinessException(ResultCode.NOT_FOUND,
                        "PDF 文件不存在或不可读，请改用 md 格式下载");
            }
            return new ReportDownloadPayload(fileBase, "application/pdf", path, null);
        }
        String md = content.getContentMarkdown();
        if (md == null) {
            md = "";
        }
        return new ReportDownloadPayload(fileBase, "text/markdown; charset=UTF-8", null, md);
    }

    // ==================== 异步投递 ====================

    /**
     * 在事务提交后投递异步 Worker，避免在事务提交前触发 Worker 读取未提交的 task。
     *
     * <p>对照 MatchingServiceImpl 批量路径的 afterCommit 模式：当存在活动事务同步时
     * 注册 {@link TransactionSynchronization#afterCommit()}，否则直接投递（无事务场景，
     * 如单测未开启事务同步时回退到同步投递）。</p>
     *
     * @param taskId 报告任务 ID
     */
    private void scheduleWorkerAfterCommit(Long taskId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    analysisReportWorker.execute(taskId);
                }
            });
            return;
        }
        analysisReportWorker.execute(taskId);
    }

    // ==================== 范围展开 ====================

    /**
     * 按范围类型展开子树节点。
     *
     * <p>ALL_CHAIN 时返回链下全部节点 ID；SELECTED_NODES 时按 node_path 前缀
     * 展开选中节点的完整子树，去重后返回。</p>
     */
    private List<Long> resolveScope(AnalysisScopeType scopeType,
                                    List<Long> selectedNodeIds,
                                    List<ChainNode> chainNodes) {
        if (scopeType == AnalysisScopeType.ALL_CHAIN) {
            return chainNodes.stream()
                    .map(ChainNode::getId)
                    .sorted()
                    .collect(Collectors.toList());
        }
        Map<Long, String> pathMap = chainNodes.stream()
                .collect(Collectors.toMap(ChainNode::getId, ChainNode::getNodePath, (a, b) -> a));
        Set<Long> resolved = new HashSet<>();
        for (Long nodeId : selectedNodeIds) {
            String path = pathMap.get(nodeId);
            if (path == null || path.isEmpty()) {
                continue;
            }
            for (ChainNode node : chainNodes) {
                String np = node.getNodePath();
                if (np != null && np.startsWith(path)) {
                    resolved.add(node.getId());
                }
            }
        }
        List<Long> sorted = new ArrayList<>(resolved);
        Collections.sort(sorted);
        return sorted;
    }

    // ==================== VO 转换 ====================

    private AnalysisReportTaskVO toTaskVO(AnalysisReportTask task, String chainName) {
        AnalysisReportTaskVO vo = new AnalysisReportTaskVO();
        vo.setId(task.getId());
        vo.setChainId(task.getChainId());
        vo.setChainName(chainName);
        vo.setReportType(task.getReportType());
        vo.setScopeType(task.getScopeType());
        vo.setSelectedNodeIds(task.getSelectedNodeIds());
        vo.setResolvedNodeIds(task.getResolvedNodeIds());
        vo.setStatus(task.getStatus());
        vo.setRetryFromTaskId(task.getRetryFromTaskId());
        vo.setRetryCount(task.getRetryCount());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCreatedBy(task.getCreatedBy());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        vo.setCompletedAt(task.getCompletedAt());
        return vo;
    }

    private AnalysisReportDetailVO toDetailVO(AnalysisReportTask task,
                                              AnalysisReportContent content,
                                              String chainName) {
        AnalysisReportDetailVO vo = new AnalysisReportDetailVO();
        vo.setId(task.getId());
        vo.setChainId(task.getChainId());
        vo.setChainName(chainName);
        vo.setReportType(task.getReportType());
        vo.setScopeType(task.getScopeType());
        vo.setStatus(task.getStatus());
        vo.setRetryFromTaskId(task.getRetryFromTaskId());
        vo.setRetryCount(task.getRetryCount());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCreatedBy(task.getCreatedBy());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setCompletedAt(task.getCompletedAt());
        if (content != null) {
            vo.setTitle(content.getTitle());
            vo.setContentMarkdown(content.getContentMarkdown());
            vo.setContentHtml(content.getContentHtml());
            vo.setPdfFilePath(content.getPdfFilePath());
            vo.setMetricsSnapshot(content.getMetricsSnapshot());
        }
        return vo;
    }

    // ==================== 工具 ====================

    /**
     * 将报告标题转换为安全的文件名片段（去除路径分隔符、控制字符）。
     */
    private String sanitizeFileName(String title) {
        if (title == null) {
            return null;
        }
        String s = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private AnalysisReportType parseReportType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "报告类型不能为空");
        }
        try {
            return AnalysisReportType.valueOf(reportType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "不支持的报告类型：" + reportType + "（第一版仅支持 HEALTH_ANALYSIS）");
        }
    }

    private AnalysisScopeType parseScopeType(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "范围类型不能为空");
        }
        try {
            return AnalysisScopeType.valueOf(scopeType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "不支持的范围类型：" + scopeType + "（仅支持 SELECTED_NODES、ALL_CHAIN）");
        }
    }

    private Map<Long, String> loadChainNameMap(Set<Long> chainIds) {
        if (chainIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Chain> chains = chainMapper.selectBatchIds(chainIds);
        Map<Long, String> map = new LinkedHashMap<>();
        for (Chain chain : chains) {
            map.put(chain.getId(), chain.getChainName());
        }
        return map;
    }

    private String buildInputSnapshot(CreateAnalysisReportRequest request,
                                      Chain chain, int resolvedCount) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("chainId", request.getChainId());
            snapshot.put("chainName", chain.getChainName());
            snapshot.put("reportType", request.getReportType());
            snapshot.put("scopeType", request.getScopeType());
            snapshot.put("selectedNodeIds", request.getNodeIds());
            snapshot.put("resolvedNodeCount", resolvedCount);
            snapshot.put("options", request.getOptions());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            log.warn("build input snapshot failed: chainId={}, reportType={}, reason={}",
                    request.getChainId(), request.getReportType(), ex.getMessage());
            return null;
        }
    }

    private String currentUsernameSafe() {
        try {
            return git.ggwzz.security.util.SecurityUtils.currentUsername();
        } catch (Exception ex) {
            return null;
        }
    }
}