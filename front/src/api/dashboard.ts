import { get } from "./http";
import { isMockApiEnabled } from "./mode";
import { mockApi } from "./mock";
import type { DashboardOverview } from "@/types/domain";

export function fetchDashboardOverview(): Promise<DashboardOverview> {
  if (isMockApiEnabled()) return mockApi.fetchDashboard();
  return get("/dashboard/overview");
}
