import { describe, expect, it } from "vitest";
import {
  buildMatchingOperationLogs,
  buildBatchMatchingRequest,
  candidateLinkTip,
  getBatchChainName,
  getBatchEnterpriseName,
  canCancelTask,
  canDecideResult,
  canRetryTask,
  candidateRiskTip,
  flattenChainNodes,
  pickActiveResult,
  isLowConfidenceCandidate,
  isTaskRunningStatus,
  matchingDecisionTip,
  matchingTaskStatusTip,
} from "./matchingViewModel";
import type { EnterpriseListItem, MatchingResult, MatchingTask } from "@/types/domain";

describe("matching view model", () => {
  it("flattens nested chain nodes for manual override selection", () => {
    const nodes = [
      {
        id: 1,
        name: "新能源汽车产业链",
        children: [
          { id: 2, name: "动力电池", children: [] },
          {
            id: 3,
            name: "电驱系统",
            children: [{ id: 4, name: "驱动电机", children: [] }],
          },
        ],
      },
    ];

    expect(flattenChainNodes(nodes)).toEqual([
      { id: 1, label: "新能源汽车产业链" },
      { id: 2, label: "新能源汽车产业链 / 动力电池" },
      { id: 3, label: "新能源汽车产业链 / 电驱系统" },
      { id: 4, label: "新能源汽车产业链 / 电驱系统 / 驱动电机" },
    ]);
  });

  it("allows decisions only for pending recommendations", () => {
    expect(canDecideResult(resultWithDecision("PENDING"))).toBe(true);
    expect(canDecideResult(resultWithDecision(undefined))).toBe(true);
    expect(canDecideResult(resultWithDecision("ACCEPTED"))).toBe(false);
    expect(canDecideResult(resultWithDecision("REJECTED"))).toBe(false);
    expect(canDecideResult(resultWithDecision("MANUAL"))).toBe(false);
  });

  it("recognizes running, cancellable and retryable task states", () => {
    expect(isTaskRunningStatus("PROCESSING")).toBe(true);
    expect(isTaskRunningStatus("RETRYING")).toBe(true);
    expect(isTaskRunningStatus("FAILED")).toBe(false);
    expect(canCancelTask("PROCESSING")).toBe(true);
    expect(canCancelTask("RETRYING")).toBe(false);
    expect(canRetryTask("FAILED")).toBe(true);
    expect(canRetryTask("TIMEOUT")).toBe(true);
    expect(canRetryTask("CANCELLED")).toBe(true);
    expect(canRetryTask("COMPLETED")).toBe(false);
  });

  it("explains why processed recommendations cannot be rejected again", () => {
    expect(matchingDecisionTip(resultWithDecision("ACCEPTED"))).toContain(
      "已采纳",
    );
    expect(matchingDecisionTip(resultWithDecision("PENDING"))).toBe("");
  });

  it("marks low-confidence candidates as not recommended for automatic linking", () => {
    const lowConfidence = {
      ...resultWithDecision("PENDING"),
      confidence: 0.42,
      reason: "低置信候选，不建议自动挂链",
    };
    const normalConfidence = {
      ...resultWithDecision("PENDING"),
      confidence: 0.78,
    };

    expect(isLowConfidenceCandidate(lowConfidence)).toBe(true);
    expect(candidateRiskTip(lowConfidence)).toContain("不建议自动挂链");
    expect(isLowConfidenceCandidate(normalConfidence)).toBe(false);
    expect(candidateRiskTip(normalConfidence)).toBe("");
  });

  it("blocks already linked candidate decisions and explains the duplicate link", () => {
    const alreadyLinked = {
      ...resultWithDecision("PENDING"),
      alreadyLinked: true,
      linkWarning: "该企业已挂接到此候选节点，请勿重复采纳。",
    };

    expect(canDecideResult(alreadyLinked)).toBe(false);
    expect(candidateLinkTip(alreadyLinked)).toContain("已挂接到此候选节点");
  });

  it("surfaces task-level existing chain link warnings", () => {
    const task = {
      ...taskWithResults([resultWithDecision("PENDING")]),
      alreadyLinkedInChain: true,
      existingLinkedNodeIds: [101],
      linkWarning: "该企业已在当前产业链存在挂链关系，采纳前请复核。",
    };

    expect(candidateLinkTip(task.results[0], task)).toContain(
      "已在当前产业链存在挂链关系",
    );
  });

  it("does not select the first recommendation while manually choosing a node", () => {
    const task = taskWithResults([resultWithDecision("PENDING")]);

    expect(pickActiveResult(task, undefined, 203)).toBeNull();
    expect(pickActiveResult(task, 1, 203)?.resultId).toBe(1);
    expect(pickActiveResult(task, undefined, undefined)?.resultId).toBe(1);
  });

  it("builds batch matching request from all and selected scopes", () => {
    expect(
      buildBatchMatchingRequest({
        enterpriseScope: "ALL",
        chainScope: "SELECTED",
        selectedEnterpriseIds: [100],
        selectedChainIds: [1, 2],
      }),
    ).toEqual({
      allEnterprises: true,
      allChains: false,
      chainIds: [1, 2],
    });

    expect(
      buildBatchMatchingRequest({
        enterpriseScope: "SELECTED",
        chainScope: "ALL",
        selectedEnterpriseIds: [100],
        selectedChainIds: [1, 2],
      }),
    ).toEqual({
      allEnterprises: false,
      enterpriseIds: [100],
      allChains: true,
    });
  });

  it("prefers task display names over option-map fallbacks in batch rows", () => {
    const task = {
      ...taskWithResults([resultWithDecision("PENDING")]),
      enterpriseName: "宁德时代新能源科技股份有限公司",
      chainName: "新能源汽车产业链",
    };

    expect(getBatchEnterpriseName(task, new Map())).toBe(
      "宁德时代新能源科技股份有限公司",
    );
    expect(getBatchChainName(task, new Map())).toBe("新能源汽车产业链");
  });

  it("builds expandable operation logs with revoke targets", () => {
    const enterprise: EnterpriseListItem = {
      id: 100,
      name: "测试企业",
      status: "ACTIVE",
      linkedNodes: [
        {
          nodeId: 301,
          nodeName: "动力电池",
          nodePath: "新能源产业链 / 动力电池",
          source: "AI",
          createdAt: "2026-07-03 10:00:00",
        },
      ],
    };
    const task = taskWithResults([
      {
        ...resultWithDecision("PENDING"),
        nodePath: "新能源产业链 / 电驱系统",
        confidence: 0.74,
        reason: "语义相似度 0.71；规则置信度 0.80",
        evidence: "vector_similarity=0.71",
      },
    ]);

    const logs = buildMatchingOperationLogs({
      enterprise,
      task,
      statusLabel: "COMPLETED",
    });

    expect(logs[0].content).toBe("生成 1 条分析候选节点");
    expect(logs[0].details?.[0].title).toContain("新能源产业链 / 电驱系统");
    expect(logs[0].details?.[0].description).toContain("综合置信度：0.74");
    expect(logs[0].revoke).toEqual({ type: "TASK", taskId: 1 });
    expect(logs[1].revoke).toEqual({
      type: "LINK",
      nodeId: 301,
      enterpriseId: 100,
    });
  });

  it("does not expose task revoke action after a task has already been cancelled", () => {
    const task = {
      ...taskWithResults([resultWithDecision("PENDING")]),
      status: "CANCELLED",
    };

    const logs = buildMatchingOperationLogs({
      task,
      statusLabel: "CANCELLED",
    });

    expect(logs[0].revoke).toBeUndefined();
  });

  it("explains timeout and cancellation task statuses for the page", () => {
    expect(matchingTaskStatusTip("TIMEOUT")).toContain("超时");
    expect(matchingTaskStatusTip("CANCELLED")).toContain("取消");
    expect(matchingTaskStatusTip("RETRYING")).toContain("重试");
  });
});

function resultWithDecision(decision?: string): MatchingResult {
  return {
    resultId: 1,
    nodeId: 101,
    nodePath: "产业链 / 节点",
    confidence: 0.8,
    reason: "测试推荐",
    decision,
  };
}

function taskWithResults(results: MatchingResult[]): MatchingTask {
  return {
    taskId: 1,
    enterpriseId: 100,
    chainId: 1,
    status: "COMPLETED",
    results,
  };
}
