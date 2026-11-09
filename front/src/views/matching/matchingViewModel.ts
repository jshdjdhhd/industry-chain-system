import type {
  ChainNode,
  EnterpriseListItem,
  MatchingOperationLog,
  MatchingResult,
  MatchingTask,
  MatchingTaskStatus,
} from "@/types/domain";
import type { CreateBatchMatchingTaskRequest } from "@/api/matching";

const LOW_CONFIDENCE_THRESHOLD = 0.65;

export type BatchMatchingScope = "ALL" | "SELECTED";

export interface BatchMatchingSelection {
  enterpriseScope: BatchMatchingScope;
  chainScope: BatchMatchingScope;
  selectedEnterpriseIds: number[];
  selectedChainIds: number[];
}

export interface FlatChainNode {
  id: number;
  label: string;
}

export interface BatchTaskStatusSummary {
  total: number;
  processing: number;
  completed: number;
  failed: number;
  cancelled: number;
  timeout: number;
  accepted: number;
  rejected: number;
  pending: number;
}

type BatchDisplayTask = MatchingTask & {
  enterpriseName?: string | null;
  chainName?: string | null;
};

export function flattenChainNodes(
  nodes: ChainNode[],
  prefix = "",
): FlatChainNode[] {
  return nodes.flatMap((node) => {
    const label = prefix ? `${prefix} / ${node.name}` : node.name;
    return [
      { id: node.id, label },
      ...flattenChainNodes(node.children ?? [], label),
    ];
  });
}

export function canDecideResult(result: MatchingResult | null | undefined) {
  if (!result || result.alreadyLinked) return false;
  return !result.decision || result.decision === "PENDING";
}

export function pickActiveResult(
  task: MatchingTask | null | undefined,
  activeResultId: number | undefined,
  manualNodeId: number | undefined,
) {
  if (!task?.results.length) return null;
  if (activeResultId) {
    return (
      task.results.find((result) => result.resultId === activeResultId) ?? null
    );
  }
  if (manualNodeId) return null;
  return task.results[0] ?? null;
}

export function canCancelTask(status: MatchingTaskStatus | null | undefined) {
  return status === "PROCESSING";
}

export function canRetryTask(status: MatchingTaskStatus | null | undefined) {
  return status === "FAILED" || status === "TIMEOUT" || status === "CANCELLED";
}

export function isTaskRunningStatus(
  status: MatchingTaskStatus | null | undefined,
) {
  return status === "PROCESSING" || status === "RETRYING";
}

export function matchingTaskStatusTip(
  status: MatchingTaskStatus | null | undefined,
) {
  switch (status) {
    case "PROCESSING":
      return "任务正在计算推荐结果，可继续等待或取消任务。";
    case "RETRYING":
      return "任务正在重试并重新计算推荐结果，请稍后刷新状态。";
    case "COMPLETED":
      return "任务已完成，可查看候选节点并处理推荐结果。";
    case "FAILED":
      return "任务执行失败，可重试任务或稍后刷新状态。";
    case "TIMEOUT":
      return "后端任务执行超时，可重试任务或稍后刷新状态。";
    case "CANCELLED":
      return "任务已取消，可按需重新发起重试任务。";
    default:
      return "";
  }
}

export function matchingDecisionTip(result: MatchingResult | null | undefined) {
  if (!result || canDecideResult(result)) return "";
  if (result.alreadyLinked) {
    return candidateLinkTip(result);
  }
  if (result.decision === "ACCEPTED") {
    return "该推荐已采纳，如需撤销请到节点企业列表解绑正式关系。";
  }
  if (result.decision === "REJECTED") {
    return "该推荐已拒绝，拒绝原因已经保存。";
  }
  if (result.decision === "MANUAL") {
    return "该节点已由人工确认挂链，不再需要采纳或拒绝。";
  }
  return "该推荐已处理，不能重复采纳或拒绝。";
}

export function isLowConfidenceCandidate(
  result: MatchingResult | null | undefined,
) {
  if (!result) return false;
  return (
    result.confidence < LOW_CONFIDENCE_THRESHOLD ||
    result.reason.includes("不建议自动挂链") ||
    result.evidence?.includes("LOW_CONFIDENCE") === true
  );
}

export function candidateRiskTip(result: MatchingResult | null | undefined) {
  if (!isLowConfidenceCandidate(result)) return "";
  return "低置信候选，不建议自动挂链；可作为人工复核备选节点。";
}

export function candidateLinkTip(
  result: MatchingResult | null | undefined,
  task?: MatchingTask | null,
) {
  if (!result) return "";
  if (result.linkWarning) return result.linkWarning;
  if (result.alreadyLinked) {
    return "该企业已挂接到此候选节点，请勿重复采纳。";
  }
  if (task?.linkWarning) return task.linkWarning;
  if (task?.alreadyLinkedInChain) {
    return "该企业已在当前产业链存在挂链关系，采纳前请复核。";
  }
  return "";
}

export function buildBatchMatchingRequest(
  selection: BatchMatchingSelection,
): CreateBatchMatchingTaskRequest {
  const allEnterprises = selection.enterpriseScope === "ALL";
  const allChains = selection.chainScope === "ALL";
  return {
    allEnterprises,
    ...(allEnterprises
      ? {}
      : { enterpriseIds: uniquePositiveIds(selection.selectedEnterpriseIds) }),
    allChains,
    ...(allChains
      ? {}
      : { chainIds: uniquePositiveIds(selection.selectedChainIds) }),
  };
}

export function getBatchEnterpriseName(
  task: MatchingTask,
  enterpriseMap: Map<number, string>,
): string {
  const displayTask = task as BatchDisplayTask;
  return (
    nonBlank(displayTask.enterpriseName) ??
    enterpriseMap.get(task.enterpriseId) ??
    `企业#${task.enterpriseId}`
  );
}

export function getBatchChainName(
  task: MatchingTask,
  chainMap: Map<number, string>,
): string {
  const displayTask = task as BatchDisplayTask;
  return (
    nonBlank(displayTask.chainName) ??
    chainMap.get(task.chainId) ??
    `产业链#${task.chainId}`
  );
}

export function summarizeBatchTasks(
  tasks: MatchingTask[],
): BatchTaskStatusSummary {
  const summary: BatchTaskStatusSummary = {
    total: tasks.length,
    processing: 0,
    completed: 0,
    failed: 0,
    cancelled: 0,
    timeout: 0,
    accepted: 0,
    rejected: 0,
    pending: 0,
  };
  for (const task of tasks) {
    switch (task.status) {
      case "PROCESSING":
      case "RETRYING":
        summary.processing++;
        break;
      case "COMPLETED":
        summary.completed++;
        break;
      case "FAILED":
        summary.failed++;
        break;
      case "CANCELLED":
        summary.cancelled++;
        break;
      case "TIMEOUT":
        summary.timeout++;
        break;
    }
    for (const result of task.results) {
      switch (result.decision) {
        case "ACCEPTED":
          summary.accepted++;
          break;
        case "REJECTED":
          summary.rejected++;
          break;
        default:
          summary.pending++;
          break;
      }
    }
  }
  return summary;
}

export function buildBatchOperationLogs(
  tasks: MatchingTask[],
  enterpriseMap: Map<number, string>,
  chainMap: Map<number, string>,
): MatchingOperationLog[] {
  const logs: MatchingOperationLog[] = [];

  for (const task of tasks) {
    if (task.status !== "COMPLETED" || task.results.length === 0) continue;

    const enterpriseName = getBatchEnterpriseName(task, enterpriseMap);
    const chainName = getBatchChainName(task, chainMap);
    const bestResult = task.results[0];
    const displayPath = bestResult.nodePath || "未知节点";
    const canRevoke = task.results.some(
      (r) => r.decision === "ACCEPTED" || r.decision === "MANUAL",
    );

    logs.push({
      id: `batch-task-${task.taskId}`,
      createdAt: "批量任务",
      operator: "系统",
      action: "批量智能推荐",
      content: `${enterpriseName} × ${chainName} → ${displayPath}`,
      result: getTaskResultLabel(task),
      details: task.results.map((result, index) => {
        const nodePath = result.nodePath || "未知节点";
        const confidence = Number(result.confidence).toFixed(2);
        const riskTip = isLowConfidenceCandidate(result)
          ? "⚠️ 低置信候选，建议人工复核"
          : "✅ 置信度达标，可考虑采纳";
        const decisionLabel = getDecisionLabel(result.decision);

        return {
          title: `${index + 1}. ${nodePath}`,
          description: [
            `推荐等级：${getConfidenceLevel(result.confidence)}`,
            `综合置信度：${confidence}`,
            `推荐理由：${result.reason || "无"}`,
            `关键词证据：${result.evidence || "主营产品与经营范围命中"}`,
            `处理状态：${decisionLabel}`,
            `风险提示：${riskTip}`,
          ].join("\n"),
          evidence: result.evidence,
        };
      }),
      ...(canRevoke
        ? { revoke: { type: "TASK" as const, taskId: task.taskId } }
        : {}),
    });
  }

  return logs;
}

export function buildMatchingOperationLogs(input: {
  enterprise?: EnterpriseListItem | null;
  task?: MatchingTask | null;
  statusLabel: string;
}): MatchingOperationLog[] {
  const logs: MatchingOperationLog[] = [];
  if (input.task) {
    const countText = `${input.task.results.length} 条分析候选节点`;
    const taskCanRevoke =
      input.task.status === "COMPLETED" && input.task.results.length > 0;
    logs.push({
      id: `task-${input.task.taskId}`,
      createdAt: "当前会话",
      operator: "系统",
      action: "人工智能分析",
      content: `生成 ${countText}`,
      result: input.statusLabel,
      details: input.task.results.map((result, index) => {
        const nodePath = result.nodePath || "未知节点";
        const confidence = Number(result.confidence).toFixed(2);
        const riskTip = isLowConfidenceCandidate(result)
          ? "⚠️ 低置信候选，建议人工复核"
          : "✅ 置信度达标，可考虑采纳";
        const decisionLabel = getDecisionLabel(result.decision);

        return {
          title: `${index + 1}. ${nodePath}`,
          description: [
            `分析等级：${getConfidenceLevel(result.confidence)}`,
            `综合置信度：${confidence}`,
            `分析依据：${result.reason || "无"}`,
            `分析证据：${result.evidence || "大模型分析结果"}`,
            `处理状态：${decisionLabel}`,
            `风险提示：${riskTip}`,
          ].join("\n"),
          evidence: result.evidence,
        };
      }),
      ...(taskCanRevoke
        ? { revoke: { type: "TASK" as const, taskId: input.task.taskId } }
        : {}),
    });
  }

  if (input.enterprise) {
    for (const node of input.enterprise.linkedNodes ?? []) {
      const displayPath =
        node.nodePath || node.nodeName || `未知节点(${node.nodeId})`;

      logs.push({
        id: `link-${input.enterprise.id}-${node.nodeId}`,
        createdAt: node.createdAt || "当前会话",
        operator: node.source === "AI" ? "系统" : "管理员",
        action: node.source === "AI" ? "智能挂链" : "人工挂链",
        content: `${input.enterprise.name} 挂接至 ${displayPath}`,
        result: node.nodeMissing ? "节点缺失" : "成功",
        details: [
          {
            title: displayPath,
            description: [
              `来源：${node.source === "AI" ? "AI 推荐" : "人工确认"}`,
              node.confidence == null
                ? ""
                : `置信度：${Number(node.confidence).toFixed(2)}`,
              node.nodeMissing ? "⚠️ 节点数据缺失或已删除" : "",
            ]
              .filter(Boolean)
              .join("\n"),
          },
        ],
        revoke: node.nodeMissing
          ? undefined
          : {
              type: "LINK",
              nodeId: node.nodeId,
              enterpriseId: input.enterprise.id,
            },
      });
    }
  }

  return logs;
}

function getConfidenceLevel(confidence: number): string {
  if (confidence >= 0.8) return "建议采纳";
  if (confidence >= 0.65) return "建议复核";
  return "不建议采纳";
}

function getDecisionLabel(decision?: string): string {
  switch (decision) {
    case "ACCEPTED":
      return "已采纳";
    case "REJECTED":
      return "已拒绝";
    case "MANUAL":
      return "人工确认";
    default:
      return "待处理";
  }
}

function getTaskResultLabel(task: MatchingTask): string {
  const accepted = task.results.filter((r) => r.decision === "ACCEPTED").length;
  const rejected = task.results.filter((r) => r.decision === "REJECTED").length;
  const manual = task.results.filter((r) => r.decision === "MANUAL").length;
  const pending = task.results.filter(
    (r) => !r.decision || r.decision === "PENDING",
  ).length;

  const parts: string[] = [];
  if (accepted > 0) parts.push(`${accepted}个已采纳`);
  if (rejected > 0) parts.push(`${rejected}个已拒绝`);
  if (manual > 0) parts.push(`${manual}个人工确认`);
  if (pending > 0) parts.push(`${pending}个待处理`);
  return parts.join("，") || "已完成";
}

function uniquePositiveIds(ids: number[]) {
  return Array.from(
    new Set(ids.filter((id) => Number.isFinite(id) && id > 0)),
  );
}

function nonBlank(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}
