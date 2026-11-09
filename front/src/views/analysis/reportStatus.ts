/**
 * 报告状态文案与标签类型的单点定义，供列表页与详情页复用。
 */
export const statusTextMap: Record<string, string> = {
  CREATED: "已创建",
  PROCESSING: "处理中",
  COMPLETED: "已完成",
  FAILED: "失败",
  CANCELLED: "已取消",
};

export const statusTagType: Record<
  string,
  "info" | "warning" | "success" | "danger"
> = {
  CREATED: "info",
  PROCESSING: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "info",
};

export function statusText(status: string): string {
  return statusTextMap[status] ?? status;
}

export function statusType(status: string): "info" | "warning" | "success" | "danger" {
  return statusTagType[status] ?? "info";
}
