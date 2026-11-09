import type { Component } from "vue";
import {
  CircleCheck,
  CircleClose,
  Clock,
  Connection,
  DataLine,
  FolderChecked,
  Refresh,
  Warning,
} from "@element-plus/icons-vue";

export interface StatusMeta {
  label: string;
  tone: "blue" | "green" | "amber" | "red" | "gray" | "purple";
  icon?: Component;
}

const statusMap: Record<string, StatusMeta> = {
  UNREGISTERED: { label: "未入库", tone: "amber", icon: Clock },
  ACTIVE: { label: "存续", tone: "green", icon: CircleCheck },
  OUTBOUND: { label: "出库", tone: "gray", icon: FolderChecked },
  ENABLED: { label: "启用", tone: "green", icon: CircleCheck },
  DISABLED: { label: "停用", tone: "gray", icon: CircleClose },
  PENDING: { label: "待确认", tone: "amber", icon: Warning },
  ACCEPTED: { label: "已采纳", tone: "green", icon: CircleCheck },
  REJECTED: { label: "已拒绝", tone: "red", icon: CircleClose },
  MANUAL: { label: "人工", tone: "blue", icon: Connection },
  AI: { label: "AI 推荐", tone: "purple", icon: DataLine },
  IMPORT: { label: "导入", tone: "gray", icon: FolderChecked },
  SUCCESS: { label: "成功", tone: "green", icon: CircleCheck },
  PARTIAL_FAILED: { label: "部分失败", tone: "amber", icon: Warning },
  FAILED: { label: "失败", tone: "red", icon: CircleClose },
  CANCELLED: { label: "已取消", tone: "gray", icon: CircleClose },
  TIMEOUT: { label: "已超时", tone: "amber", icon: Clock },
  RETRYING: { label: "重试中", tone: "blue", icon: Refresh },
};

export function getStatusMeta(status: string): StatusMeta {
  return statusMap[status] ?? { label: status, tone: "gray" };
}
