import { get } from "./http";
import type { PageResult, OperationLog } from "@/types/domain";

export function fetchOperationLogs(params: {
  pageNum: number;
  pageSize: number;
  operator?: string;
  action?: string;
  targetType?: string;
  startTime?: string;
  endTime?: string;
}): Promise<PageResult<OperationLog>> {
  return get("/operation-logs", { params });
}
