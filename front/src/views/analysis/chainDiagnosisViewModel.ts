import type { ChainHealthLevel, DiagnosisTreeNode } from "@/types/domain";

/** G6 v4 节点 model：树图所需的最小字段集合。 */
export interface G6DiagnosisNodeModel {
  id: string;
  label: string;
  subLabel: string;
  nodeId: number;
  nodeName: string;
  nodePath?: string | null;
  nodeLevel: number;
  keywords?: string | null;
  description?: string | null;
  directEnterpriseCount: number;
  subtreeEnterpriseCount: number;
  healthScore: number;
  healthLevel: ChainHealthLevel;
  healthLabel: string;
  weakReasons: string[];
  size: number;
  fill: string;
  stroke: string;
  collapsed: boolean;
  children: G6DiagnosisNodeModel[];
}

interface NodePalette {
  fill: string;
  stroke: string;
}

const PALETTE: Record<string, NodePalette> = {
  HEALTHY: { fill: "#16a34a", stroke: "#15803d" },
  NORMAL: { fill: "#d9a90f", stroke: "#a16207" },
  WEAK: { fill: "#f97316", stroke: "#c2410c" },
  RISK: { fill: "#dc2626", stroke: "#991b1b" },
  EMPTY: { fill: "#dc2626", stroke: "#7f1d1d" },
};

const DEFAULT_PALETTE: NodePalette = { fill: "#94a3b8", stroke: "#475467" };

/**
 * 节点大小：子树无企业时返回 24；否则按健康分映射到 [28, 64]。
 */
export function resolveNodeSize(score: number, count: number): number {
  if (count === 0) return 24;
  return Math.max(28, Math.min(64, 24 + Math.round(score / 2)));
}

/**
 * 节点配色：严格按后端 healthLevel 取色，未知等级使用中性灰兜底。
 */
export function resolveNodePalette(
  healthLevel: ChainHealthLevel | undefined,
): NodePalette {
  if (!healthLevel) return DEFAULT_PALETTE;
  return PALETTE[healthLevel] ?? DEFAULT_PALETTE;
}

/**
 * 健康分兜底：后端未返回时按子树企业数估算（0~100）。
 */
export function resolveHealthScore(
  score: number | null | undefined,
  subtreeEnterpriseCount: number,
): number {
  if (typeof score === "number" && !Number.isNaN(score)) return score;
  if (subtreeEnterpriseCount <= 0) return 0;
  if (subtreeEnterpriseCount >= 10) return 90;
  return Math.min(90, subtreeEnterpriseCount * 9);
}

/**
 * 健康等级兜底：后端未返回时按子树企业数估算。
 */
export function resolveHealthLevel(
  healthLevel: ChainHealthLevel | undefined,
  subtreeEnterpriseCount: number,
): ChainHealthLevel {
  if (healthLevel) return healthLevel;
  if (subtreeEnterpriseCount <= 0) return "EMPTY";
  if (subtreeEnterpriseCount <= 2) return "RISK";
  if (subtreeEnterpriseCount <= 5) return "WEAK";
  if (subtreeEnterpriseCount <= 10) return "NORMAL";
  return "HEALTHY";
}

const HEALTH_LABEL_FALLBACK: Record<string, string> = {
  HEALTHY: "健康",
  NORMAL: "一般",
  WEAK: "偏弱",
  RISK: "风险",
  EMPTY: "空白",
};

export function resolveHealthLabel(
  healthLabel: string | null | undefined,
  healthLevel: ChainHealthLevel,
): string {
  if (healthLabel) return healthLabel;
  return HEALTH_LABEL_FALLBACK[healthLevel] ?? "未知";
}

/**
 * 节点标签：第二行展示健康等级 + 子树企业数。
 */
export function buildNodeSubLabel(
  healthLabel: string,
  subtreeEnterpriseCount: number,
): string {
  return `${healthLabel} · ${subtreeEnterpriseCount} 家`;
}

/**
 * 将后端 DiagnosisTreeNode 递归转换为 G6 节点 model。
 * 第二级及以下的非叶子节点默认折叠，减少初始视觉负担。
 */
export function toG6DiagnosisModel(
  node: DiagnosisTreeNode,
  options?: { collapsedDepth?: number },
): G6DiagnosisNodeModel {
  const collapsedDepth = options?.collapsedDepth ?? 2;
  const subtreeCount = node.subtreeEnterpriseCount ?? 0;
  const directCount = node.directEnterpriseCount ?? 0;
  const healthLevel = resolveHealthLevel(node.healthLevel, subtreeCount);
  const healthScore = resolveHealthScore(node.healthScore, subtreeCount);
  const healthLabel = resolveHealthLabel(node.healthLabel, healthLevel);
  const palette = resolveNodePalette(healthLevel);
  const size = resolveNodeSize(healthScore, subtreeCount);
  const hasChildren = (node.children ?? []).length > 0;
  const collapsed =
    hasChildren && node.nodeLevel >= collapsedDepth ? true : false;
  return {
    id: `node-${node.nodeId}`,
    label: node.nodeName,
    subLabel: buildNodeSubLabel(healthLabel, subtreeCount),
    nodeId: node.nodeId,
    nodeName: node.nodeName,
    nodePath: node.nodePath ?? null,
    nodeLevel: node.nodeLevel,
    keywords: node.keywords ?? null,
    description: node.description ?? null,
    directEnterpriseCount: directCount,
    subtreeEnterpriseCount: subtreeCount,
    healthScore,
    healthLevel,
    healthLabel,
    weakReasons: node.weakReasons ?? [],
    size,
    fill: palette.fill,
    stroke: palette.stroke,
    collapsed,
    children: (node.children ?? []).map((child) =>
      toG6DiagnosisModel(child, options),
    ),
  };
}

/**
 * 把虚拟根（产业链根）转换为 G6 TreeGraph 所需的根 data 对象。
 */
export function toG6TreeData(
  root: DiagnosisTreeNode,
  options?: { collapsedDepth?: number },
): G6DiagnosisNodeModel {
  return toG6DiagnosisModel(root, options);
}

/**
 * 切换报告选择模式下某节点的选中状态，返回新的选中集合（不可变更新）。
 */
export function toggleSelectedNodeId(
  selected: Set<number>,
  nodeId: number,
): Set<number> {
  const next = new Set(selected);
  if (next.has(nodeId)) {
    next.delete(nodeId);
  } else {
    next.add(nodeId);
  }
  return next;
}

/**
 * 判断整棵诊断树的所有一级节点是否都被选中（用于"全选"判定）。
 */
export function isAllFirstLevelSelected(
  root: DiagnosisTreeNode,
  selected: Set<number>,
): boolean {
  const firstLevel = root.children ?? [];
  if (firstLevel.length === 0) return false;
  return firstLevel.every((node) => selected.has(node.nodeId));
}

/**
 * 收集选中的节点 id 列表（用于提交报告创建请求）。
 */
export function collectSelectedNodeIds(selected: Set<number>): number[] {
  return Array.from(selected).sort((a, b) => a - b);
}

/**
 * 在 G6 model 树中按 nodeId 查找节点 model。
 *
 * @since 7.3 前置声明：当前诊断页未直接调用，报告详情页（7.5）将用于
 * 根据后端返回的 nodeIds 定位并高亮对应 G6 节点，故保留导出。
 */
export function findG6ModelByNodeId(
  root: G6DiagnosisNodeModel,
  nodeId: number,
): G6DiagnosisNodeModel | undefined {
  if (root.nodeId === nodeId) return root;
  for (const child of root.children) {
    const found = findG6ModelByNodeId(child, nodeId);
    if (found) return found;
  }
  return undefined;
}
