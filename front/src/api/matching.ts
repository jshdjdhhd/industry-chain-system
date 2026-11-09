import { get, post } from "./http";
import { isMockApiEnabled } from "./mode";
import { mockApi } from "./mock";
import type { BatchMatchingTaskResult, MatchingTask } from "@/types/domain";

export interface CreateBatchMatchingTaskRequest {
  allEnterprises: boolean;
  enterpriseIds?: number[];
  allChains: boolean;
  chainIds?: number[];
}

export function createMatchingTask(data: {
  enterpriseId: number;
  chainId: number;
}): Promise<MatchingTask> {
  if (isMockApiEnabled()) return mockApi.createMatchingTask(data);
  return post("/matching/tasks/async", data, { timeout: 15_000 });
}

export function createBatchMatchingTasks(
  data: CreateBatchMatchingTaskRequest,
): Promise<BatchMatchingTaskResult> {
  if (isMockApiEnabled()) return mockApi.createBatchMatchingTasks(data);
  return post("/matching/tasks/batch-async", data, { timeout: 20_000 });
}

export function fetchMatchingTask(taskId: number): Promise<MatchingTask> {
  if (isMockApiEnabled()) return mockApi.fetchMatchingTask(taskId);
  return get(`/matching/tasks/${taskId}`, { timeout: 30_000 });
}

export function fetchInitialMatchingTask(): Promise<MatchingTask | null> {
  if (isMockApiEnabled()) return mockApi.fetchMatchingTask(9001);
  return Promise.resolve(null);
}

export function cancelMatchingTask(taskId: number): Promise<MatchingTask> {
  if (isMockApiEnabled()) return mockApi.cancelMatchingTask(taskId);
  return post(`/matching/tasks/${taskId}/cancel`);
}

export function revokeMatchingTask(taskId: number): Promise<MatchingTask> {
  if (isMockApiEnabled()) return mockApi.revokeMatchingTask(taskId);
  return post(`/matching/tasks/${taskId}/revoke`);
}

export function retryMatchingTask(taskId: number): Promise<MatchingTask> {
  if (isMockApiEnabled()) return mockApi.retryMatchingTask(taskId);
  return post(`/matching/tasks/${taskId}/retry`);
}

export function acceptMatchingResult(resultId: number): Promise<{
  resultId: number;
  decision: string;
}> {
  if (isMockApiEnabled()) return mockApi.acceptMatchingResult(resultId);
  return post(`/matching/results/${resultId}/accept`);
}

export function rejectMatchingResult(
  resultId: number,
  rejectReason: string,
): Promise<{
  resultId: number;
  decision: string;
}> {
  if (isMockApiEnabled())
    return mockApi.rejectMatchingResult(resultId, rejectReason);
  return post(`/matching/results/${resultId}/reject`, { rejectReason });
}

export function manualConfirmMatching(data: {
  enterpriseId: number;
  nodeId: number;
  taskId?: number;
  sourceResultId?: number;
  reason: string;
}): Promise<{
  enterpriseId: number;
  nodeId: number;
  source: string;
  linked: boolean;
}> {
  if (isMockApiEnabled()) return mockApi.manualConfirmMatching(data);
  return post("/matching/manual-confirm", data);
}

export interface BatchQueryVO {
  total: number;
  processingCount: number;
  completedCount: number;
  failedCount: number;
  acceptedCount: number;
  rejectedCount: number;
  tasks: MatchingTask[];
}

export function fetchBatchTasks(taskIds: number[]): Promise<BatchQueryVO> {
  if (isMockApiEnabled()) return mockApi.fetchBatchTasks(taskIds);
  return post("/matching/tasks/batch-query", { taskIds });
}

// AI 聊天接口已迁移至 @/api/aiChat
