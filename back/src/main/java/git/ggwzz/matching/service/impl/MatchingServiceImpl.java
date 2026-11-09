package git.ggwzz.matching.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.chain.service.EnterpriseNodeRelService;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.entity.Enterprise;
import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.matching.dto.BatchQueryRequest;
import git.ggwzz.matching.dto.CreateBatchMatchingTaskRequest;
import git.ggwzz.matching.dto.CreateMatchingTaskRequest;
import git.ggwzz.matching.dto.ManualConfirmRequest;
import git.ggwzz.matching.dto.RejectMatchingResultRequest;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.entity.MatchingTask;
import git.ggwzz.matching.enums.MatchingDecision;
import git.ggwzz.matching.enums.MatchingTaskStatus;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.matching.mapper.MatchingTaskMapper;
import git.ggwzz.matching.service.MatchingProvider;
import git.ggwzz.matching.service.MatchingService;
import git.ggwzz.matching.vo.BatchQueryVO;
import git.ggwzz.matching.vo.ManualConfirmVO;
import git.ggwzz.matching.vo.BatchMatchingTaskVO;
import git.ggwzz.matching.vo.MatchingDecisionVO;
import git.ggwzz.matching.vo.MatchingResultVO;
import git.ggwzz.matching.vo.MatchingTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 智能挂链推荐服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingServiceImpl implements MatchingService {

    private static final int MAX_BATCH_TASK_PAIRS = 500;
    private static final String EXISTING_CHAIN_LINK_WARNING =
            "该企业已在当前产业链存在挂链关系，采纳前请复核。";
    private static final String DUPLICATE_NODE_LINK_WARNING =
            "该企业已挂接到此候选节点，请勿重复采纳。";

    private final MatchingTaskMapper matchingTaskMapper;
    private final MatchingResultMapper matchingResultMapper;
    private final MatchingProvider matchingProvider;
    private final HybridEmbeddingMatchingProvider hybridEmbeddingMatchingProvider;
    private final EnterpriseNodeRelService enterpriseNodeRelService;
    private final ChainNodeQueryService chainNodeQueryService;
    private final MatchingTaskWorker matchingTaskWorker;
    private final EnterpriseMapper enterpriseMapper;
    private final ChainMapper chainMapper;
    private final MatchingResultPersistenceSupport persistenceSupport;
    private final MatchingBatchLocalEngine batchLocalEngine;
    @Qualifier("matchingTaskExecutor")
    private final Executor matchingTaskExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchingTaskVO createTask(CreateMatchingTaskRequest request, Long operator) {
        validateRequest(request);

        MatchingTask task = buildTask(request, operator, MatchingTaskStatus.COMPLETED, "SYNC");
        matchingTaskMapper.insert(task);

        List<MatchingResult> recommendations = matchingProvider.match(
                request.getEnterpriseId(), request.getChainId());
        if (recommendations == null) {
            recommendations = List.of();
        }

        int rankNo = 1;
        for (MatchingResult result : recommendations) {
            result.setTaskId(task.getId());
            result.setRankNo(rankNo++);
            result.setDecision(MatchingDecision.PENDING.name());
            persistenceSupport.prepareForRemotePersistence(result);
            matchingResultMapper.insert(result);
        }

        log.info("matching task completed synchronously: taskId={}, resultCount={}",
                task.getId(), recommendations.size());
        return getTask(task.getId());
    }

    @Override
    public MatchingTaskVO createTaskAsync(CreateMatchingTaskRequest request, Long operator) {
        validateRequest(request);

        MatchingTask task = buildTask(request, operator, MatchingTaskStatus.PROCESSING, "ASYNC");
        matchingTaskMapper.insert(task);

        log.info("matching task submitted asynchronously: taskId={}, enterpriseId={}, chainId={}",
                task.getId(), request.getEnterpriseId(), request.getChainId());
        matchingTaskWorker.execute(task.getId(), request.getEnterpriseId(), request.getChainId());
        return getTask(task.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchMatchingTaskVO createBatchTasksAsync(CreateBatchMatchingTaskRequest request, Long operator) {
        BatchScope scope = resolveBatchScope(request);
        long totalPairs = (long) scope.enterpriseIds().size() * scope.chainIds().size();
        if (totalPairs > MAX_BATCH_TASK_PAIRS) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "批量智能挂链一次最多提交 " + MAX_BATCH_TASK_PAIRS + " 个组合，请缩小企业或产业链范围");
        }

        // 1. 批量创建任务记录（在同一事务内，失败则全部回滚）
        List<MatchingTask> tasks = new ArrayList<>((int) totalPairs);
        LocalDateTime now = LocalDateTime.now();
        for (Long enterpriseId : scope.enterpriseIds()) {
            for (Long chainId : scope.chainIds()) {
                CreateMatchingTaskRequest singleRequest = new CreateMatchingTaskRequest();
                singleRequest.setEnterpriseId(enterpriseId);
                singleRequest.setChainId(chainId);
                MatchingTask task = buildTask(singleRequest, operator, MatchingTaskStatus.PROCESSING,
                        "BATCH_LOCAL", hybridEmbeddingMatchingProvider);
                task.setCreatedAt(now);
                task.setUpdatedAt(now);
                tasks.add(task);
            }
        }
        int inserted = matchingTaskMapper.insertBatch(tasks);
        if (inserted != tasks.size()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "批量任务创建失败，请稍后重试");
        }
        if (tasks.stream().anyMatch(task -> task.getId() == null)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "批量任务 ID 回填失败，请稍后重试");
        }

        // 2. 先同步消化推荐缓存，缓存命中的演示任务可立即完成；未命中再提交给批量引擎异步执行。
        List<Long> enterpriseIds = scope.enterpriseIds();
        List<Long> chainIds = scope.chainIds();
        List<MatchingTask> remainingTasks = tasks;
        int cachedTaskCount = 0;
        try {
            MatchingBatchLocalEngine.CachePrecheckOutcome cacheOutcome =
                    batchLocalEngine.completeCachedTasks(tasks, enterpriseIds, chainIds);
            if (cacheOutcome != null) {
                remainingTasks = cacheOutcome.remainingTasks() == null ? tasks : cacheOutcome.remainingTasks();
                cachedTaskCount = cacheOutcome.cachedCount();
            }
        } catch (Exception ex) {
            log.warn("batch recommendation cache precheck failed, fallback to async execution", ex);
        }
        if (!remainingTasks.isEmpty()) {
            List<Long> remainingEnterpriseIds = remainingTasks.stream()
                    .map(MatchingTask::getEnterpriseId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            List<Long> remainingChainIds = remainingTasks.stream()
                    .map(MatchingTask::getChainId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            submitBatchExecutionAfterCommit(remainingTasks, remainingEnterpriseIds, remainingChainIds);
        }

        // 3. 构建响应。若缓存命中，直接返回已完成结果；否则保持轻量响应，避免额外查询。
        List<MatchingTaskVO> taskVOs = cachedTaskCount > 0
                ? queryBatchTaskVOs(tasks)
                : buildSubmittedTaskVOs(tasks);

        BatchMatchingTaskVO vo = new BatchMatchingTaskVO();
        vo.setEnterpriseCount(scope.enterpriseIds().size());
        vo.setChainCount(scope.chainIds().size());
        vo.setTotalPairs((int) totalPairs);
        vo.setSubmittedCount(tasks.size());
        vo.setTasks(taskVOs);
        log.info("batch matching tasks submitted: enterpriseCount={}, chainCount={}, submittedCount={}",
                vo.getEnterpriseCount(), vo.getChainCount(), vo.getSubmittedCount());
        return vo;
    }

    @Override
    public MatchingTaskVO getTask(Long taskId) {
        MatchingTask task = requireTask(taskId);
        List<MatchingResult> results = listTaskResults(taskId);
        return toTaskVO(task, results);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchingTaskVO cancelTask(Long taskId, Long operator) {
        MatchingTask task = requireTask(taskId);
        MatchingTaskStatus status = requireStatus(task);
        if (status == MatchingTaskStatus.CANCELLED) {
            return getTask(taskId);
        }
        if (!status.isCancellable()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有执行中的任务才能取消");
        }

        LocalDateTime cancelledAt = LocalDateTime.now();
        MatchingTask update = new MatchingTask();
        update.setId(taskId);
        update.setStatus(MatchingTaskStatus.CANCELLED.name());
        update.setCancelledBy(String.valueOf(operator));
        update.setCancelledAt(cancelledAt);
        update.setErrorMessage("任务已取消");
        update.setInputSnapshot(MatchingTaskSnapshotSupport.cancellationSnapshot(
                task.getInputSnapshot(), operator, cancelledAt));
        matchingTaskMapper.updateById(update);

        log.info("matching task cancelled: taskId={}, operator={}", taskId, operator);
        return getTask(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchingTaskVO revokeTask(Long taskId, Long operator) {
        MatchingTask task = requireTask(taskId);
        List<MatchingResult> results = listTaskResults(taskId);

        for (MatchingResult result : results) {
            if (MatchingDecision.ACCEPTED.name().equals(result.getDecision())
                    || MatchingDecision.MANUAL.name().equals(result.getDecision())) {
                unlinkSafely(result.getNodeId(), task.getEnterpriseId(), operator);
            }
        }

        matchingResultMapper.delete(new LambdaQueryWrapper<MatchingResult>()
                .eq(MatchingResult::getTaskId, taskId));

        LocalDateTime revokedAt = LocalDateTime.now();
        MatchingTask update = new MatchingTask();
        update.setId(taskId);
        update.setStatus(MatchingTaskStatus.CANCELLED.name());
        update.setCancelledBy(String.valueOf(operator));
        update.setCancelledAt(revokedAt);
        update.setErrorMessage("任务已撤回，推荐结果已清空");
        update.setInputSnapshot(MatchingTaskSnapshotSupport.cancellationSnapshot(
                task.getInputSnapshot(), operator, revokedAt));
        matchingTaskMapper.updateById(update);

        log.info("matching task revoked: taskId={}, operator={}, resultCount={}",
                taskId, operator, results.size());
        return getTask(taskId);
    }

    @Override
    public MatchingTaskVO retryTask(Long taskId, Long operator) {
        MatchingTask sourceTask = requireTask(taskId);
        MatchingTaskStatus sourceStatus = requireStatus(sourceTask);
        if (!sourceStatus.canRetry()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有失败、取消或超时任务才能重试");
        }

        CreateMatchingTaskRequest request = new CreateMatchingTaskRequest();
        request.setEnterpriseId(sourceTask.getEnterpriseId());
        request.setChainId(sourceTask.getChainId());

        MatchingTask retryTask = buildTask(request, operator, MatchingTaskStatus.RETRYING, "RETRY");
        retryTask.setRetryFromTaskId(sourceTask.getId());
        retryTask.setRetryCount((sourceTask.getRetryCount() == null ? 0 : sourceTask.getRetryCount()) + 1);
        retryTask.setInputSnapshot(MatchingTaskSnapshotSupport.retrySnapshot(sourceTask, operator));
        matchingTaskMapper.insert(retryTask);

        log.info("matching task retried: sourceTaskId={}, retryTaskId={}, retryCount={}",
                sourceTask.getId(), retryTask.getId(), retryTask.getRetryCount());
        matchingTaskWorker.execute(retryTask.getId(), retryTask.getEnterpriseId(), retryTask.getChainId());
        return getTask(retryTask.getId());
    }

    private void unlinkSafely(Long nodeId, Long enterpriseId, Long operator) {
        try {
            enterpriseNodeRelService.unlink(nodeId, enterpriseId, operator);
        } catch (BusinessException ex) {
            if (!Objects.equals(ResultCode.NOT_FOUND.getCode(), ex.getCode())) {
                throw ex;
            }
            log.info("skip unlink during revoke because relation no longer exists: enterpriseId={}, nodeId={}",
                    enterpriseId, nodeId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchingDecisionVO acceptResult(Long resultId, Long operator) {
        MatchingResult result = requireResult(resultId);
        if (!MatchingDecision.PENDING.name().equals(result.getDecision())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只能采纳待决策的推荐结果");
        }

        MatchingTask task = requireTask(result.getTaskId());

        result.setDecision(MatchingDecision.ACCEPTED.name());
        result.setDecidedBy(String.valueOf(operator));
        result.setDecidedAt(LocalDateTime.now());
        matchingResultMapper.updateById(result);

        enterpriseNodeRelService.linkByAi(
                task.getEnterpriseId(),
                result.getNodeId(),
                result.getConfidence(),
                result.getReason(),
                operator);

        MatchingDecisionVO vo = new MatchingDecisionVO();
        vo.setResultId(resultId);
        vo.setDecision(MatchingDecision.ACCEPTED.name());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchingDecisionVO rejectResult(Long resultId, RejectMatchingResultRequest request, Long operator) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        MatchingResult result = requireResult(resultId);
        if (!MatchingDecision.PENDING.name().equals(result.getDecision())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只能拒绝待决策的推荐结果");
        }

        result.setDecision(MatchingDecision.REJECTED.name());
        result.setRejectReason(request.getRejectReason());
        result.setDecidedBy(String.valueOf(operator));
        result.setDecidedAt(LocalDateTime.now());
        matchingResultMapper.updateById(result);

        MatchingDecisionVO vo = new MatchingDecisionVO();
        vo.setResultId(resultId);
        vo.setDecision(MatchingDecision.REJECTED.name());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManualConfirmVO manualConfirm(ManualConfirmRequest request, Long operator) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }

        MatchingTask relatedTask = null;
        if (request.getTaskId() != null) {
            relatedTask = requireTask(request.getTaskId());
            validateManualConfirmTaskScope(request, relatedTask);
        }

        enterpriseNodeRelService.linkManually(
                request.getEnterpriseId(),
                request.getNodeId(),
                request.getReason(),
                operator);

        if (relatedTask != null) {
            recordManualOverride(request, operator, relatedTask);
        }

        ManualConfirmVO vo = new ManualConfirmVO();
        vo.setEnterpriseId(request.getEnterpriseId());
        vo.setNodeId(request.getNodeId());
        vo.setSource("MANUAL");
        vo.setLinked(true);
        return vo;
    }

    private void validateManualConfirmTaskScope(ManualConfirmRequest request, MatchingTask task) {
        if (!Objects.equals(task.getEnterpriseId(), request.getEnterpriseId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "人工改选企业与推荐任务不一致");
        }
        List<ChainNodeCandidateDTO> chainNodes = chainNodeQueryService.listByChainId(task.getChainId());
        boolean nodeInTaskChain = chainNodes != null && chainNodes.stream()
                .anyMatch(node -> Objects.equals(node.getNodeId(), request.getNodeId()));
        if (!nodeInTaskChain) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "人工改选节点不属于推荐任务产业链");
        }
    }

    private void recordManualOverride(ManualConfirmRequest request,
                                      Long operator,
                                      MatchingTask task) {
        List<MatchingResult> taskResults = listTaskResults(request.getTaskId());
        MatchingResult selectedResult = taskResults.stream()
                .filter(result -> Objects.equals(result.getNodeId(), request.getNodeId()))
                .findFirst()
                .orElse(null);

        rejectSourceResultIfNeeded(request, operator, selectedResult);
        if (selectedResult != null) {
            markExistingResultAsManual(selectedResult, request, operator);
            return;
        }

        MatchingResult manualResult = new MatchingResult();
        manualResult.setTaskId(request.getTaskId());
        manualResult.setNodeId(request.getNodeId());
        manualResult.setConfidence(new BigDecimal("1.0000"));
        manualResult.setReason(manualReason(request));
        manualResult.setEvidence(manualEvidence(request));
        manualResult.setRankNo(nextRankNo(taskResults));
        manualResult.setDecision(MatchingDecision.MANUAL.name());
        manualResult.setDecidedBy(String.valueOf(operator));
        manualResult.setDecidedAt(LocalDateTime.now());
        persistenceSupport.prepareForRemotePersistence(manualResult);
        matchingResultMapper.insert(manualResult);
    }

    private void rejectSourceResultIfNeeded(ManualConfirmRequest request,
                                            Long operator,
                                            MatchingResult selectedResult) {
        if (request.getSourceResultId() == null) {
            return;
        }
        MatchingResult source = matchingResultMapper.selectById(request.getSourceResultId());
        if (source == null || !Objects.equals(source.getTaskId(), request.getTaskId())) {
            return;
        }
        if (selectedResult != null && Objects.equals(selectedResult.getId(), source.getId())) {
            return;
        }
        if (!MatchingDecision.PENDING.name().equals(source.getDecision())) {
            return;
        }
        source.setDecision(MatchingDecision.REJECTED.name());
        source.setRejectReason("已人工改选到节点：" + chainNodeQueryService.buildNodePath(request.getNodeId()));
        source.setDecidedBy(String.valueOf(operator));
        source.setDecidedAt(LocalDateTime.now());
        matchingResultMapper.updateById(source);
    }

    private void markExistingResultAsManual(MatchingResult selectedResult,
                                            ManualConfirmRequest request,
                                            Long operator) {
        selectedResult.setDecision(MatchingDecision.MANUAL.name());
        selectedResult.setConfidence(new BigDecimal("1.0000"));
        selectedResult.setReason(manualReason(request));
        selectedResult.setEvidence(manualEvidence(request));
        selectedResult.setRejectReason(null);
        selectedResult.setDecidedBy(String.valueOf(operator));
        selectedResult.setDecidedAt(LocalDateTime.now());
        persistenceSupport.prepareForRemotePersistence(selectedResult);
        matchingResultMapper.updateById(selectedResult);
    }

    private MatchingTaskVO toTaskVO(MatchingTask task, List<MatchingResult> results) {
        return toTaskVO(task, results, new HashMap<>(),
                loadEnterpriseNameMap(List.of(task)),
                loadChainNameMap(List.of(task)));
    }

    private List<MatchingTaskVO> buildSubmittedTaskVOs(List<MatchingTask> tasks) {
        Map<Long, String> enterpriseNameMap = loadEnterpriseNameMap(tasks);
        Map<Long, String> chainNameMap = loadChainNameMap(tasks);
        return tasks.stream()
                .map(t -> {
                    MatchingTaskVO vo = new MatchingTaskVO();
                    vo.setTaskId(t.getId());
                    vo.setEnterpriseId(t.getEnterpriseId());
                    vo.setEnterpriseName(enterpriseNameMap.get(t.getEnterpriseId()));
                    vo.setChainId(t.getChainId());
                    vo.setChainName(chainNameMap.get(t.getChainId()));
                    vo.setStatus(t.getStatus());
                    vo.setResults(List.of());
                    return vo;
                })
                .toList();
    }

    private List<MatchingTaskVO> queryBatchTaskVOs(List<MatchingTask> tasks) {
        BatchQueryRequest queryRequest = new BatchQueryRequest();
        queryRequest.setTaskIds(tasks.stream()
                .map(MatchingTask::getId)
                .filter(Objects::nonNull)
                .toList());
        return batchQueryTasks(queryRequest).getTasks();
    }

    private MatchingTaskVO toTaskVO(MatchingTask task,
                                    List<MatchingResult> results,
                                    Map<Long, String> nodePathCache) {
        return toTaskVO(task, results, nodePathCache, Map.of(), Map.of());
    }

    private MatchingTaskVO toTaskVO(MatchingTask task,
                                    List<MatchingResult> results,
                                    Map<Long, String> nodePathCache,
                                    Map<Long, String> enterpriseNameMap,
                                    Map<Long, String> chainNameMap) {
        MatchingTaskVO vo = new MatchingTaskVO();
        vo.setTaskId(task.getId());
        vo.setEnterpriseId(task.getEnterpriseId());
        vo.setEnterpriseName(enterpriseNameMap.get(task.getEnterpriseId()));
        vo.setChainId(task.getChainId());
        vo.setChainName(chainNameMap.get(task.getChainId()));
        vo.setStatus(task.getStatus());
        vo.setRetryFromTaskId(task.getRetryFromTaskId());
        vo.setRetryCount(task.getRetryCount());
        vo.setCancelledBy(task.getCancelledBy());
        vo.setCancelledAt(task.getCancelledAt());
        vo.setErrorMessage(task.getErrorMessage());
        List<Long> existingLinkedNodeIds = listExistingLinkedNodeIds(task);
        Set<Long> existingLinkedNodeIdSet = new LinkedHashSet<>(existingLinkedNodeIds);
        vo.setAlreadyLinkedInChain(!existingLinkedNodeIds.isEmpty());
        vo.setExistingLinkedNodeIds(existingLinkedNodeIds);
        vo.setLinkWarning(existingLinkedNodeIds.isEmpty()
                ? null
                : EXISTING_CHAIN_LINK_WARNING);
        vo.setResults(results.stream()
                .map(result -> convertToVO(result, nodePathCache, existingLinkedNodeIdSet))
                .collect(Collectors.toList()));
        return vo;
    }

    private List<Long> listExistingLinkedNodeIds(MatchingTask task) {
        if (task.getEnterpriseId() == null || task.getChainId() == null) {
            return List.of();
        }
        Set<Long> linkedNodeIds = enterpriseNodeRelService
                .listLinkedNodeIdsByEnterpriseAndChain(
                        task.getEnterpriseId(), task.getChainId());
        if (linkedNodeIds == null || linkedNodeIds.isEmpty()) {
            return List.of();
        }
        return linkedNodeIds.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private Map<Long, String> loadEnterpriseNameMap(List<MatchingTask> tasks) {
        List<Long> enterpriseIds = tasks.stream()
                .map(MatchingTask::getEnterpriseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (enterpriseIds.isEmpty()) {
            return Map.of();
        }
        List<Enterprise> enterprises = enterpriseMapper.selectBatchIds(enterpriseIds);
        if (enterprises == null) {
            return Map.of();
        }
        return enterprises.stream()
                .filter(enterprise -> enterprise.getId() != null
                        && enterprise.getEnterpriseName() != null)
                .collect(Collectors.toMap(
                        Enterprise::getId,
                        Enterprise::getEnterpriseName,
                        (left, right) -> left));
    }

    private Map<Long, String> loadChainNameMap(List<MatchingTask> tasks) {
        List<Long> chainIds = tasks.stream()
                .map(MatchingTask::getChainId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (chainIds.isEmpty()) {
            return Map.of();
        }
        List<Chain> chains = chainMapper.selectBatchIds(chainIds);
        if (chains == null) {
            return Map.of();
        }
        return chains.stream()
                .filter(chain -> chain.getId() != null && chain.getChainName() != null)
                .collect(Collectors.toMap(
                        Chain::getId,
                        Chain::getChainName,
                        (left, right) -> left));
    }

    private MatchingResultVO convertToVO(MatchingResult result,
                                         Map<Long, String> nodePathCache,
                                         Set<Long> existingLinkedNodeIds) {
        MatchingResultVO vo = new MatchingResultVO();
        vo.setResultId(result.getId());
        vo.setNodeId(result.getNodeId());
        vo.setConfidence(result.getConfidence());
        vo.setReason(result.getReason());
        vo.setEvidence(persistenceSupport.cleanEvidence(result.getEvidence()));
        vo.setDecision(result.getDecision());
        vo.setRejectReason(result.getRejectReason());
        boolean alreadyLinked = result.getNodeId() != null
                && existingLinkedNodeIds.contains(result.getNodeId());
        vo.setAlreadyLinked(alreadyLinked);
        vo.setLinkWarning(alreadyLinked ? DUPLICATE_NODE_LINK_WARNING : null);
        if (result.getNodeId() != null) {
            vo.setNodePath(nodePathCache.computeIfAbsent(
                    result.getNodeId(), chainNodeQueryService::buildNodePath));
        }
        return vo;
    }

    private MatchingTask buildTask(CreateMatchingTaskRequest request,
                                   Long operator,
                                   MatchingTaskStatus status,
                                   String executionMode) {
        return buildTask(request, operator, status, executionMode, matchingProvider);
    }

    private MatchingTask buildTask(CreateMatchingTaskRequest request,
                                   Long operator,
                                   MatchingTaskStatus status,
                                   String executionMode,
                                   MatchingProvider provider) {
        MatchingTask task = new MatchingTask();
        task.setEnterpriseId(request.getEnterpriseId());
        task.setChainId(request.getChainId());
        String modelName = provider == null ? null : provider.getModelName();
        task.setModelName(modelName == null || modelName.isBlank() ? "RULE_BASED" : modelName);
        task.setStatus(status.name());
        task.setRetryCount(0);
        task.setCreatedBy(String.valueOf(operator));
        task.setInputSnapshot(MatchingTaskSnapshotSupport.initialSnapshot(executionMode));
        return task;
    }

    private List<MatchingResult> listTaskResults(Long taskId) {
        LambdaQueryWrapper<MatchingResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MatchingResult::getTaskId, taskId)
                .orderByAsc(MatchingResult::getRankNo);
        List<MatchingResult> results = matchingResultMapper.selectList(wrapper);
        return results == null ? List.of() : results;
    }

    private MatchingTask requireTask(Long taskId) {
        MatchingTask task = matchingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private MatchingResult requireResult(Long resultId) {
        MatchingResult result = matchingResultMapper.selectById(resultId);
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "推荐结果不存在");
        }
        return result;
    }

    private MatchingTaskStatus requireStatus(MatchingTask task) {
        try {
            MatchingTaskStatus status = MatchingTaskStatus.from(task.getStatus());
            if (status == null) {
                throw new IllegalArgumentException("null status");
            }
            return status;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "任务状态非法");
        }
    }

    private int nextRankNo(List<MatchingResult> taskResults) {
        return taskResults.stream()
                .map(MatchingResult::getRankNo)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    private String manualReason(ManualConfirmRequest request) {
        String reason = request.getReason() == null || request.getReason().isBlank()
                ? "人工确认该企业更适合目标节点"
                : request.getReason().trim();
        return "人工确认：" + reason;
    }

    private String manualEvidence(ManualConfirmRequest request) {
        String sourceResult = request.getSourceResultId() == null
                ? "null"
                : String.valueOf(request.getSourceResultId());
        return "{\"model\":\"MANUAL_OVERRIDE\",\"source_result_id\":" + sourceResult + "}";
    }

    private void validateRequest(CreateMatchingTaskRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
    }

    private BatchScope resolveBatchScope(CreateBatchMatchingTaskRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        List<Long> enterpriseIds = Boolean.TRUE.equals(request.getAllEnterprises())
                ? listAllActiveEnterpriseIds()
                : normalizeIds(request.getEnterpriseIds(), "企业");
        List<Long> chainIds = Boolean.TRUE.equals(request.getAllChains())
                ? listAllChainIds()
                : normalizeIds(request.getChainIds(), "产业链");
        if (enterpriseIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "没有可用于智能挂链的存续企业");
        }
        if (chainIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "没有可用于智能挂链的产业链");
        }
        return new BatchScope(enterpriseIds, chainIds);
    }

    private List<Long> listAllActiveEnterpriseIds() {
        List<Enterprise> enterprises = enterpriseMapper.selectList(new QueryWrapper<Enterprise>()
                .select("id")
                .eq("status", "ACTIVE")
                .orderByAsc("id"));
        if (enterprises == null) {
            return List.of();
        }
        return enterprises.stream()
                .map(Enterprise::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Long> listAllChainIds() {
        List<Chain> chains = chainMapper.selectList(new QueryWrapper<Chain>()
                .select("id")
                .orderByAsc("id"));
        if (chains == null) {
            return List.of();
        }
        return chains.stream()
                .map(Chain::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Long> normalizeIds(List<Long> ids, String label) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择" + label);
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                normalized.add(id);
            }
        }
        if (normalized.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择有效的" + label);
        }
        return new ArrayList<>(normalized);
    }

    @Override
    public BatchQueryVO batchQueryTasks(BatchQueryRequest request) {
        if (request == null || request.getTaskIds() == null || request.getTaskIds().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "任务ID列表不能为空");
        }

        List<Long> taskIds = request.getTaskIds().stream().distinct().toList();

        // 批量查询任务（单条 SQL）
        List<MatchingTask> taskEntities = matchingTaskMapper.selectBatchByIds(taskIds);
        Map<Long, MatchingTask> taskMap = taskEntities.stream()
                .collect(Collectors.toMap(MatchingTask::getId, t -> t, (l, r) -> l));

        // 批量查询结果（单条 SQL）
        List<MatchingResult> allResults = matchingResultMapper.selectByTaskIds(taskIds);
        Map<Long, List<MatchingResult>> resultsByTaskId = allResults.stream()
                .collect(Collectors.groupingBy(MatchingResult::getTaskId));

        // 组装 VO。同一批结果里节点会重复出现，先批量加载路径避免 N+1 查询。
        Map<Long, String> nodePathCache = new HashMap<>(chainNodeQueryService.buildNodePathMap(
                allResults.stream()
                        .map(MatchingResult::getNodeId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()));
        Map<Long, String> enterpriseNameMap = loadEnterpriseNameMap(taskEntities);
        Map<Long, String> chainNameMap = loadChainNameMap(taskEntities);
        List<MatchingTaskVO> tasks = new ArrayList<>();
        for (Long taskId : taskIds) {
            MatchingTask taskEntity = taskMap.get(taskId);
            if (taskEntity == null) {
                continue;
            }
            List<MatchingResult> results = resultsByTaskId.getOrDefault(taskId, List.of());
            tasks.add(toTaskVO(taskEntity, results, nodePathCache, enterpriseNameMap, chainNameMap));
        }

        BatchQueryVO vo = new BatchQueryVO();
        vo.setTotal(tasks.size());
        vo.setProcessingCount((int) tasks.stream()
                .filter(t -> "PROCESSING".equals(t.getStatus()) || "RETRYING".equals(t.getStatus()))
                .count());
        vo.setCompletedCount((int) tasks.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .count());
        vo.setFailedCount((int) tasks.stream()
                .filter(t -> "FAILED".equals(t.getStatus()) || "TIMEOUT".equals(t.getStatus()))
                .count());
        vo.setAcceptedCount((int) tasks.stream()
                .flatMap(t -> t.getResults().stream())
                .filter(r -> "ACCEPTED".equals(r.getDecision()))
                .count());
        vo.setRejectedCount((int) tasks.stream()
                .flatMap(t -> t.getResults().stream())
                .filter(r -> "REJECTED".equals(r.getDecision()))
                .count());
        vo.setTasks(tasks);
        return vo;
    }

    private record BatchScope(List<Long> enterpriseIds, List<Long> chainIds) {
    }

    private void submitBatchExecutionAfterCommit(List<MatchingTask> tasks,
                                                 List<Long> enterpriseIds,
                                                 List<Long> chainIds) {
        Runnable command = () -> runBatchExecutionSafely(tasks, enterpriseIds, chainIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitBatchExecution(command, tasks);
                }
            });
            return;
        }
        submitBatchExecution(command, tasks);
    }

    private void runBatchExecutionSafely(List<MatchingTask> tasks,
                                         List<Long> enterpriseIds,
                                         List<Long> chainIds) {
        try {
            batchLocalEngine.executeBatch(tasks, enterpriseIds, chainIds);
        } catch (Exception ex) {
            log.error("batch matching execution crashed, marking tasks as failed", ex);
            markBatchTasksFailed(tasks, "批量任务执行异常：" + safeErrorMessage(ex));
        }
    }

    private void submitBatchExecution(Runnable command, List<MatchingTask> tasks) {
        try {
            matchingTaskExecutor.execute(command);
        } catch (RejectedExecutionException ex) {
            log.error("matching task executor queue full, marking tasks as failed", ex);
            markBatchTasksFailed(tasks, "系统繁忙，任务队列已满，请稍后重试");
        }
    }

    private void markBatchTasksFailed(List<MatchingTask> tasks, String errorMessage) {
        for (MatchingTask task : tasks) {
            MatchingTask update = new MatchingTask();
            update.setId(task.getId());
            update.setStatus(MatchingTaskStatus.FAILED.name());
            update.setErrorMessage(errorMessage);
            matchingTaskMapper.updateById(update);
        }
    }

    private String safeErrorMessage(Exception ex) {
        String message = ex == null ? null : ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex == null ? "unknown error" : ex.getClass().getSimpleName();
        }
        return MatchingTaskSnapshotSupport.limit(message, 450);
    }
}
