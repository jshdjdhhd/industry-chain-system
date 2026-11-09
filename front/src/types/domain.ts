export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginVO {
  token: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  username: string;
  realName: string;
  permissions: string[];
}

export interface CurrentUserVO {
  userId: number;
  username: string;
  realName: string;
  roles: string[];
  permissions: string[];
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/** 企业下拉选项，仅包含 ID 与名称 */
export interface EnterpriseOption {
  id: number;
  name: string;
}

/** 产业链下拉选项，仅包含 ID 与名称 */
export interface ChainOption {
  id: number;
  name: string;
}

/** 智能挂链节点候选，不包含企业数统计 */
export interface ChainNodeCandidate {
  id: number;
  chainId: number;
  name: string;
  nodePath: string;
  keywords: string | null;
  description: string | null;
}

export interface PageQuery {
  pageNum: number;
  pageSize: number;
}

export type EnterpriseStatus = "UNREGISTERED" | "ACTIVE" | "OUTBOUND" | string;
export type MatchingTaskStatus =
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "TIMEOUT"
  | "RETRYING"
  | string;
export type MatchingDecision = "PENDING" | "ACCEPTED" | "REJECTED" | string;
export type LinkSource = "MANUAL" | "AI" | "IMPORT" | string;

export type MatchingOperationRevokeTarget =
  | { type: "TASK"; taskId: number }
  | { type: "LINK"; nodeId: number; enterpriseId: number };

export interface MatchingOperationLogDetail {
  title: string;
  description: string;
  evidence?: string;
}

export interface MatchingOperationLog {
  id: string;
  createdAt: string;
  operator: string;
  action: string;
  content: string;
  result: string;
  details?: MatchingOperationLogDetail[];
  revoke?: MatchingOperationRevokeTarget;
}

export interface Tag {
  id: number;
  name: string;
  type: string;
  color?: string | null;
  description?: string | null;
  usedCount?: number;
}

export interface LinkedNode {
  nodeId: number;
  nodeName: string;
  nodePath?: string;
  source?: LinkSource;
  confidence?: number | null;
  createdAt?: string;
  nodeMissing?: boolean;
}

export interface EnterpriseListItem {
  id: number;
  name: string;
  creditCode?: string | null;
  legalPerson?: string | null;
  industry?: string | null;
  businessScope?: string | null;
  mainProducts?: string | null;
  address?: string | null;
  outputValue?: number | null;
  taxAmount?: number | null;
  status: EnterpriseStatus;
  tags?: Tag[];
  linkedNodes?: LinkedNode[];
  createdAt?: string;
  updatedAt?: string;
}

export interface EnterpriseDetail extends EnterpriseListItem {
  operationLogs?: OperationLog[];
}

export interface EnterpriseRadarMetric {
  key: string;
  name: string;
  value: number;
  rawValue: string;
  description: string;
}

export interface EnterpriseRadar {
  metrics: EnterpriseRadarMetric[];
}

export interface SimilarEnterprise {
  enterpriseId: number;
  enterpriseName: string;
  similarity: number;
  similarityPercent: number;
  industry?: string | null;
  mainProducts?: string | null;
  reasons: string[];
}

export interface EnterpriseProfileAnalysis {
  enterpriseId: number;
  enterpriseName: string;
  radar: EnterpriseRadar;
  similarEnterprises: SimilarEnterprise[];
  generatedAt: string;
}

export interface EnterprisePayload {
  name: string;
  creditCode?: string;
  legalPerson?: string;
  businessScope?: string;
  mainProducts?: string;
  address?: string;
  outputValue?: number | null;
  taxAmount?: number | null;
}

export interface EnterpriseImportRecord {
  id: number;
  fileName: string;
  importedAt: string;
  operator: string;
  totalCount: number;
  successCount: number;
  failureCount: number;
  status: "SUCCESS" | "PARTIAL_FAILED" | "FAILED" | string;
  failures: Array<{
    rowNumber: number;
    enterpriseName: string;
    reason: string;
  }>;
}

export interface IndustryChain {
  id: number;
  name: string;
  description?: string | null;
  sortNo?: number;
  status?: string;
  nodeCount?: number;
  enterpriseCount?: number;
}

export type ChainStageType = "UPSTREAM" | "MIDSTREAM" | "DOWNSTREAM";

export interface ChainNode {
  id: number;
  chainId?: number;
  parentId?: number | null;
  name: string;
  stageType?: ChainStageType | null;
  levelNo?: number;
  nodePath?: string;
  keywords?: string | null;
  definition?: string | null;
  sortNo?: number;
  status?: string;
  directEnterpriseCount?: number;
  subtreeEnterpriseCount?: number;
  children?: ChainNode[];
}

export interface ChainNodePayload {
  parentId?: number | null;
  name: string;
  keywords?: string;
  definition?: string;
  sortNo?: number;
}

export interface UpdateChainNodePayload extends ChainNodePayload {
  parentChangeRequested: boolean;
}

export interface MoveNodeRequest {
  newParentId: number | null;
  sortOrder?: number;
}

export interface SortNodeOrder {
  nodeId: number;
  sortOrder: number;
}

export interface SortNodesRequest {
  parentId: number | null;
  nodeOrders: SortNodeOrder[];
}

export interface ChainPayload {
  name: string;
  description?: string;
  sortNo?: number;
}

export interface NodeEnterprise {
  enterpriseId: number;
  enterpriseName: string;
  source: LinkSource;
  createdAt?: string;
}

export interface NodeEnterpriseResult {
  nodeId: number;
  directEnterpriseCount: number;
  subtreeEnterpriseCount: number;
  records: NodeEnterprise[];
}

export type BatchLinkEnterpriseStatus =
  | "LINKED"
  | "ALREADY_LINKED"
  | "DUPLICATE_IN_REQUEST"
  | "FAILED"
  | string;

export interface BatchLinkEnterpriseItem {
  enterpriseId: number;
  status: BatchLinkEnterpriseStatus;
  message: string;
}

export interface BatchLinkEnterpriseResult {
  total: number;
  linkedCount: number;
  skippedCount: number;
  failedCount: number;
  items: BatchLinkEnterpriseItem[];
}

export interface MatchingResult {
  resultId: number;
  nodeId: number;
  nodePath: string;
  confidence: number;
  reason: string;
  evidence?: string;
  decision?: MatchingDecision;
  rejectReason?: string | null;
  alreadyLinked?: boolean;
  linkWarning?: string | null;
}

export interface MatchingTask {
  taskId: number;
  enterpriseId: number;
  enterpriseName?: string | null;
  chainId: number;
  chainName?: string | null;
  status: MatchingTaskStatus;
  retryFromTaskId?: number | null;
  retryCount?: number | null;
  cancelledBy?: string | null;
  cancelledAt?: string | null;
  errorMessage?: string | null;
  alreadyLinkedInChain?: boolean;
  existingLinkedNodeIds?: number[];
  linkWarning?: string | null;
  results: MatchingResult[];
}

export interface BatchMatchingTaskResult {
  enterpriseCount: number;
  chainCount: number;
  totalPairs: number;
  submittedCount: number;
  tasks: MatchingTask[];
}

export interface OperationLog {
  id: number;
  operator?: string;
  action: string;
  targetType: string;
  targetId?: number;
  createdAt: string;
  targetName?: string;
  result?: string;
}

export type ChainHealthLevel =
  | "HEALTHY"
  | "NORMAL"
  | "WEAK"
  | "RISK"
  | "EMPTY"
  | string;

export interface DiagnosisTreeNode {
  nodeId: number;
  nodeName: string;
  nodePath?: string | null;
  nodeLevel: number;
  keywords?: string | null;
  description?: string | null;
  directEnterpriseCount: number;
  subtreeEnterpriseCount: number;
  healthScore?: number | null;
  healthLevel?: ChainHealthLevel;
  healthLabel?: string | null;
  weakReasons?: string[];
  children?: DiagnosisTreeNode[];
}

export type AnalysisReportStatus =
  | "CREATED"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | string;

/** 范围类型：整链 / 选定节点（选中父节点默认分析整棵子树） */
export type AnalysisReportScopeType = "ALL_CHAIN" | "SELECTED_NODES" | string;

/** 报告类型，当前仅健康分析 */
export type AnalysisReportType = "HEALTH_ANALYSIS" | string;

/**
 * 健康分析报告任务 VO（与后端 AnalysisReportTaskVO 对齐）。
 * 后端字段为 id（非 reportId），前端统一使用 id。
 */
export interface AnalysisReportTask {
  /** 任务 ID（后端字段名 id） */
  id: number;
  chainId: number;
  chainName?: string;
  reportType: AnalysisReportType;
  scopeType: AnalysisReportScopeType;
  /** 用户选择节点 ID 列表 */
  selectedNodeIds?: number[];
  /** 展开子树后的实际节点 ID 列表 */
  resolvedNodeIds?: number[];
  status: AnalysisReportStatus;
  /** 重试来源任务 ID */
  retryFromTaskId?: number | null;
  /** 重试次数 */
  retryCount?: number;
  errorMessage?: string | null;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string | null;
  completedAt?: string | null;
}

/**
 * 健康分析报告详情 VO（与后端 AnalysisReportDetailVO 对齐）。
 * 在任务基础信息之上增加报告内容（Markdown/HTML/PDF 路径/指标快照）。
 */
export interface AnalysisReportDetail extends AnalysisReportTask {
  /** 报告标题 */
  title?: string;
  /** Markdown 源内容 */
  contentMarkdown?: string | null;
  /** HTML 预览内容（后端已清洗，前端 v-html 渲染） */
  contentHtml?: string | null;
  /** PDF 文件路径 */
  pdfFilePath?: string | null;
  /** 健康指标快照（JSON 字符串） */
  metricsSnapshot?: string | null;
}

export interface DashboardMetric {
  label: string;
  value: number | string;
  unit?: string;
  trend?: string;
  tone?: "blue" | "green" | "amber" | "purple" | "red";
}

export interface DashboardOverview {
  metrics: DashboardMetric[];
  chainDistribution: Array<{
    name: string;
    value: number;
    percent: string;
  }>;
  nodeStatistics: Array<{
    name: string;
    value: number;
    tone: string;
  }>;
  matchingSummary: {
    pending: number;
    accepted: number;
    rejected: number;
    manual: number;
    averageConfidence: number;
    weeklyRecommendations: number;
  };
  weeklyProgress: Array<{
    date: string;
    inbound: number;
    nodes: number;
    recommendations: number;
    accepted: number;
  }>;
  recentOperations: OperationLog[];
}

// ===== 系统管理模块 =====

export interface SysUserVO {
  id: number;
  username: string;
  realName: string;
  email: string;
  phone: string;
  status: number;
  roles: SysRoleVO[];
  createdAt: string;
  updatedAt: string;
}

export interface SysRoleVO {
  id: number;
  roleCode: string;
  roleName: string;
}

export interface SysRoleDetailVO extends SysRoleVO {
  permissions: SysPermissionTreeVO[];
}

export interface SysPermissionTreeVO {
  id: number;
  permissionCode: string;
  permissionName: string;
  parentId: number;
  sort: number;
  children?: SysPermissionTreeVO[];
}

export interface CreateUserRequest {
  username: string;
  password: string;
  realName?: string;
  email?: string;
  phone?: string;
  roleIds?: number[];
}

export interface UpdateUserRequest {
  realName?: string;
  email?: string;
  phone?: string;
}

export interface CreateRoleRequest {
  roleCode: string;
  roleName: string;
  permissionIds?: number[];
}

export interface UpdateRoleRequest {
  roleName: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}
// ==================== AI 聊天 ====================

export type AiChatRole = "user" | "assistant";

export type AiChatMessageStatus = "streaming" | "completed" | "failed";

export interface AiChatMessage {
  id: string;
  role: AiChatRole;
  content: string;
  status: AiChatMessageStatus;
  createdAt: number;
}

export interface AiChatConfirmMessage {
  id: string;
  role: "assistant";
  type: "confirm";
  confirmId: string;
  tool: string;
  args: Record<string, unknown>;
  description: string;
  riskLevel: "low" | "medium" | "high";
  status: "pending" | "confirmed" | "rejected" | "timeout";
  createdAt: number;
}

export type AiChatSessionMessage = AiChatMessage | AiChatConfirmMessage;

export interface AiChatHistoryItem {
  role: AiChatRole;
  content: string;
}

export interface AiChatRequestPayload {
  enterpriseId: number | null;
  chainId: number | null;
  enterpriseIds: number[];
  chainIds: number[];
  message: string;
  history: AiChatHistoryItem[];
}

export interface AiChatResponse {
  reply: string;
  model: string;
}

export interface AiChatStreamCallbacks {
  onChunk: (text: string) => void;
  onDone: () => void;
  onError: (error: string) => void;
}

export interface AiChatSession {
  id: string;
  title: string;
  enterpriseId: number | null;
  enterpriseName: string | null;
  enterpriseIds: number[];
  enterpriseNames: string[];
  chainId: number | null;
  chainName: string | null;
  chainIds: number[];
  chainNames: string[];
  messages: AiChatSessionMessage[];
  createdAt: number;
  updatedAt: number;
  streaming: boolean;
  failedRequest: AiChatRequestPayload | null;
}

export interface AiChatStreamHandle {
  abort: () => void;
}
