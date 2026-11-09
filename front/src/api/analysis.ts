import { downloadBlob, get, post } from "./http";
import { isMockApiEnabled } from "./mode";
import { mockApi } from "./mock";
import type {
  AnalysisReportDetail,
  AnalysisReportTask,
  DiagnosisTreeNode,
  PageQuery,
  PageResult,
} from "@/types/domain";

interface BackendDiagnosisTreeNodeVO {
  nodeId: number;
  nodeName: string;
  nodePath?: string | null;
  nodeLevel: number;
  keywords?: string | null;
  description?: string | null;
  directEnterpriseCount?: number;
  subtreeEnterpriseCount?: number;
  healthScore?: number | null;
  healthLevel?: string;
  healthLabel?: string | null;
  weakReasons?: string[];
  children?: BackendDiagnosisTreeNodeVO[];
}

export function mapBackendDiagnosisTree(
  node: BackendDiagnosisTreeNodeVO,
): DiagnosisTreeNode {
  return {
    nodeId: node.nodeId,
    nodeName: node.nodeName,
    nodePath: node.nodePath ?? null,
    nodeLevel: node.nodeLevel,
    keywords: node.keywords ?? null,
    description: node.description ?? null,
    directEnterpriseCount: node.directEnterpriseCount ?? 0,
    subtreeEnterpriseCount: node.subtreeEnterpriseCount ?? 0,
    healthScore: node.healthScore ?? null,
    healthLevel: node.healthLevel,
    healthLabel: node.healthLabel ?? null,
    weakReasons: node.weakReasons ?? [],
    children: (node.children ?? []).map(mapBackendDiagnosisTree),
  };
}

/**
 * 获取产业链诊断树（含健康度）。后端 7.1 已实现：
 * GET /api/analysis/chains/{chainId}/diagnosis-tree
 * 返回虚拟根（nodeId=chainId, nodeLevel=0），children 为一级节点。
 */
export function fetchDiagnosisTree(
  chainId: number,
): Promise<DiagnosisTreeNode> {
  if (isMockApiEnabled()) {
    return mockApi.fetchDiagnosisTree(chainId);
  }
  return get<BackendDiagnosisTreeNodeVO>(
    `/analysis/chains/${chainId}/diagnosis-tree`,
  ).then(mapBackendDiagnosisTree);
}

export interface CreateAnalysisReportRequest {
  chainId: number;
  reportType: "HEALTH_ANALYSIS";
  scopeType: "ALL_CHAIN" | "SELECTED_NODES";
  nodeIds: number[];
}

/**
 * 创建健康分析报告（异步任务）。后端 7.3 提供：
 * POST /api/analysis/reports
 */
export function createAnalysisReport(
  data: CreateAnalysisReportRequest,
): Promise<AnalysisReportTask> {
  if (isMockApiEnabled()) {
    return mockApi.createAnalysisReport(data);
  }
  return post<AnalysisReportTask>("/analysis/reports", data);
}

export interface AnalysisReportQuery extends PageQuery {
  chainId?: number;
  status?: string;
}

/**
 * 查询报告任务列表。后端 7.5 提供：GET /api/analysis/reports
 * 返回分页结果 { records, total, pageNum, pageSize }。
 */
export function fetchAnalysisReports(
  params: AnalysisReportQuery,
): Promise<PageResult<AnalysisReportTask>> {
  if (isMockApiEnabled()) {
    return mockApi.fetchAnalysisReports(params);
  }
  return get<PageResult<AnalysisReportTask>>("/analysis/reports", {
    params: {
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      chainId: params.chainId,
      status: params.status,
    },
  });
}

/**
 * 查询报告任务详情。后端 7.5 提供：GET /api/analysis/reports/{reportId}
 */
export function fetchAnalysisReportDetail(
  reportId: number,
): Promise<AnalysisReportDetail> {
  if (isMockApiEnabled()) {
    return mockApi.fetchAnalysisReportDetail(reportId);
  }
  return get<AnalysisReportDetail>(`/analysis/reports/${reportId}`);
}

/**
 * 重试失败/取消的报告任务。后端 7.5 提供：
 * POST /api/analysis/reports/{reportId}/retry
 */
export function retryAnalysisReport(
  reportId: number,
): Promise<AnalysisReportTask> {
  if (isMockApiEnabled()) {
    return mockApi.retryAnalysisReport(reportId);
  }
  return post<AnalysisReportTask>(`/analysis/reports/${reportId}/retry`);
}

export type AnalysisReportDownloadFormat = "pdf" | "md";

/**
 * 下载报告（PDF / MD）。后端 7.5 提供：
 * GET /api/analysis/reports/{reportId}/download?format=pdf|md
 * 返回二进制流（blob），需绕过响应拦截器。
 */
export function downloadAnalysisReport(
  reportId: number,
  format: AnalysisReportDownloadFormat,
): Promise<Blob> {
  if (isMockApiEnabled()) {
    return mockApi.downloadAnalysisReport(reportId, format);
  }
  return downloadBlob(`/analysis/reports/${reportId}/download`, {
    params: { format },
  });
}
