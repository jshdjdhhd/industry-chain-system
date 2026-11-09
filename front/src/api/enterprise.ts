import { get, post, put, remove } from "./http";
import { isMockApiEnabled } from "./mode";
import { mockApi } from "./mock";
import type {
  EnterpriseDetail,
  EnterpriseImportRecord,
  EnterpriseListItem,
  EnterpriseOption,
  EnterprisePayload,
  EnterpriseProfileAnalysis,
  EnterpriseRadar,
  PageQuery,
  PageResult,
} from "@/types/domain";

export interface EnterpriseQuery extends PageQuery {
  keyword?: string;
  status?: string;
  tagId?: number;
  nodeId?: number;
}

export interface ImportResult {
  successCount: number;
  failureCount: number;
  failures: Array<{
    rowNumber: number;
    enterpriseName: string;
    reason: string;
  }>;
}

export interface ImportRecordQuery extends PageQuery {
  keyword?: string;
  status?: string;
}

export function fetchEnterprises(
  params: EnterpriseQuery,
): Promise<PageResult<EnterpriseListItem>> {
  if (isMockApiEnabled()) return mockApi.fetchEnterprises(params);
  return get("/enterprises", { params });
}

export function fetchEnterpriseOptions(
  keyword: string,
  limit = 20,
): Promise<EnterpriseOption[]> {
  if (isMockApiEnabled()) return mockApi.fetchEnterpriseOptions(keyword, limit);
  return get("/enterprises/options", {
    params: { keyword: keyword || undefined, limit },
  });
}

export function fetchEnterprise(id: number): Promise<EnterpriseDetail> {
  if (isMockApiEnabled()) return mockApi.fetchEnterprise(id);
  return get(`/enterprises/${id}`);
}

export function fetchEnterpriseProfileAnalysis(
  id: number,
  similarLimit = 5,
): Promise<EnterpriseProfileAnalysis> {
  if (isMockApiEnabled()) {
    return mockApi.fetchEnterpriseProfileAnalysis(id, similarLimit);
  }
  return get(`/enterprises/${id}/profile-analysis`, {
    params: { similarLimit },
  });
}

export function fetchEnterpriseRadar(id: number): Promise<EnterpriseRadar> {
  if (isMockApiEnabled()) return mockApi.fetchEnterpriseRadar(id);
  return get(`/enterprises/${id}/profile-analysis/radar`);
}

export function createEnterprise(
  data: EnterprisePayload,
): Promise<EnterpriseDetail> {
  if (isMockApiEnabled()) return mockApi.createEnterprise(data);
  return post("/enterprises", data);
}

export function updateEnterprise(
  id: number,
  data: EnterprisePayload,
): Promise<EnterpriseDetail> {
  if (isMockApiEnabled()) return mockApi.updateEnterprise(id, data);
  return put(`/enterprises/${id}`, data);
}

export function deleteEnterprise(id: number): Promise<void> {
  if (isMockApiEnabled()) return mockApi.deleteEnterprise(id);
  return remove(`/enterprises/${id}`);
}

export function inboundEnterprise(id: number): Promise<void> {
  if (isMockApiEnabled()) return mockApi.inboundEnterprise(id);
  return post(`/enterprises/${id}/inbound`);
}

export function outboundEnterprise(id: number): Promise<void> {
  if (isMockApiEnabled()) return mockApi.outboundEnterprise(id);
  return post(`/enterprises/${id}/outbound`);
}

export function batchOutboundEnterprises(ids: number[]): Promise<void> {
  if (isMockApiEnabled()) return mockApi.batchOutboundEnterprises(ids);
  return post("/enterprises/batch-outbound", { ids });
}

export function batchInboundEnterprises(ids: number[]): Promise<number> {
  if (isMockApiEnabled()) return mockApi.batchInboundEnterprises(ids);
  return post("/enterprises/batch-inbound", { ids });
}

export function importEnterprises(file: File): Promise<ImportResult> {
  if (isMockApiEnabled()) return mockApi.importEnterprises();
  const formData = new FormData();
  formData.append("file", file);

  return post("/enterprises/import", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}

export function fetchEnterpriseImportRecords(
  params: ImportRecordQuery,
): Promise<PageResult<EnterpriseImportRecord>> {
  if (isMockApiEnabled()) return mockApi.fetchEnterpriseImportRecords(params);
  return get("/enterprises/import-records", { params });
}

export function bindEnterpriseTag(id: number, tagId: number): Promise<void> {
  if (isMockApiEnabled()) return mockApi.bindEnterpriseTag(id, tagId);
  return post(`/enterprises/${id}/tags`, { tagId });
}

export function unbindEnterpriseTag(id: number, tagId: number): Promise<void> {
  if (isMockApiEnabled()) return mockApi.unbindEnterpriseTag(id, tagId);
  return remove(`/enterprises/${id}/tags/${tagId}`);
}
