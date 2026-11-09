import type { ChainNode, SortNodesRequest } from "@/types/domain";

export interface FlattenedNode {
  id: number;
  name: string;
  parentId: number | null;
  position: number;
  level: number;
  pathName: string;
  node: ChainNode;
}

export interface NodeLocation {
  chainId: number;
  node: ChainNode;
  parentId: number | null;
  siblings: ChainNode[];
}

export function flattenNodeTree(nodes: ChainNode[]): FlattenedNode[] {
  const flattened: FlattenedNode[] = [];

  function visit(
    siblings: ChainNode[],
    parentId: number | null,
    parentPath: string[],
    level: number,
  ) {
    siblings.forEach((node, position) => {
      const path = [...parentPath, node.name];
      flattened.push({
        id: node.id,
        name: node.name,
        parentId,
        position,
        level,
        pathName: path.join(" / "),
        node,
      });
      visit(node.children ?? [], node.id, path, level + 1);
    });
  }

  visit(nodes, null, [], 1);
  return flattened;
}

export function collectDescendantIds(nodes: ChainNode[], nodeId: number): number[] {
  const selected = flattenNodeTree(nodes).find((entry) => entry.id === nodeId);
  if (!selected) return [];
  return getDescendantNodeIds(selected.node);
}

export function getDescendantNodeIds(node: ChainNode): number[] {
  return (node.children ?? []).flatMap((child) => [
    child.id,
    ...getDescendantNodeIds(child),
  ]);
}

export function getValidParentOptions(nodes: ChainNode[], nodeId: number): FlattenedNode[] {
  const excludedIds = new Set([nodeId, ...collectDescendantIds(nodes, nodeId)]);
  return flattenNodeTree(nodes).filter((entry) => !excludedIds.has(entry.id));
}

export function getSiblingNodes(nodes: ChainNode[], parentId: number | null): ChainNode[] {
  if (parentId === null) return [...nodes];
  const parent = flattenNodeTree(nodes).find((entry) => entry.id === parentId);
  return [...(parent?.node.children ?? [])];
}

export function moveSibling(
  siblings: ChainNode[],
  nodeId: number,
  direction: "up" | "down",
): ChainNode[] {
  const currentIndex = siblings.findIndex((node) => node.id === nodeId);
  const targetIndex = currentIndex + (direction === "up" ? -1 : 1);
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= siblings.length) {
    return [...siblings];
  }

  const reordered = [...siblings];
  [reordered[currentIndex], reordered[targetIndex]] = [
    reordered[targetIndex],
    reordered[currentIndex],
  ];
  return reordered;
}

export function insertSibling(
  siblings: ChainNode[],
  nodeId: number,
  targetPosition: number,
): ChainNode[] {
  const currentIndex = siblings.findIndex((node) => node.id === nodeId);
  if (currentIndex < 0) return [...siblings];

  const reordered = [...siblings];
  const [node] = reordered.splice(currentIndex, 1);
  const insertionIndex = Math.min(Math.max(targetPosition, 0), reordered.length);
  reordered.splice(insertionIndex, 0, node);
  return reordered;
}

export function buildSortRequest(
  parentId: number | null,
  siblings: ChainNode[],
): SortNodesRequest {
  return {
    parentId,
    nodeOrders: siblings.map((node, sortOrder) => ({
      nodeId: node.id,
      sortOrder,
    })),
  };
}

export function countSubtreeNodes(node: ChainNode): number {
  return (
    1 +
    (node.children ?? []).reduce(
      (count, child) => count + countSubtreeNodes(child),
      0,
    )
  );
}

export function findNodeLocation(
  nodeTrees: Record<number, ChainNode[]>,
  nodeId: number,
): NodeLocation | undefined {
  for (const [chainId, roots] of Object.entries(nodeTrees)) {
    const location = findNodeInSiblings(Number(chainId), roots, nodeId, null);
    if (location) return location;
  }
  return undefined;
}

export function getSiblingGroup(
  nodeTrees: Record<number, ChainNode[]>,
  chainId: number,
  parentId: number | null,
): ChainNode[] | undefined {
  if (parentId === null) return nodeTrees[chainId];
  const parent = findNodeLocation(nodeTrees, parentId);
  if (!parent || parent.chainId !== chainId) return undefined;
  parent.node.children ??= [];
  return parent.node.children;
}

export function moveNodeInTree(
  nodeTrees: Record<number, ChainNode[]>,
  id: number,
  newParentId: number | null,
  sortOrder?: number,
): ChainNode {
  const location = findNodeLocation(nodeTrees, id);
  if (!location) throw new Error("节点不存在");
  if (location.node.stageType) {
    throw new Error("阶段节点无法修改父节点");
  }
  if (newParentId === id) {
    throw new Error("节点不能移动到自身下");
  }

  const descendantIds = new Set(getDescendantNodeIds(location.node));
  if (newParentId !== null && descendantIds.has(newParentId)) {
    throw new Error("节点不能移动到自己的子节点下");
  }

  const newParent =
    newParentId === null ? undefined : findNodeLocation(nodeTrees, newParentId);
  if (newParentId !== null && !newParent) {
    throw new Error("父节点不存在");
  }
  if (newParent && newParent.chainId !== location.chainId) {
    throw new Error("不能跨产业链移动节点");
  }

  const oldIndex = location.siblings.findIndex((node) => node.id === id);
  location.siblings.splice(oldIndex, 1);
  normalizeSiblingOrder(location.siblings);

  const targetSiblings = getSiblingGroup(nodeTrees, location.chainId, newParentId);
  if (!targetSiblings) {
    throw new Error("父节点不存在");
  }
  const insertionIndex = Math.min(
    Math.max(sortOrder ?? targetSiblings.length, 0),
    targetSiblings.length,
  );
  location.node.parentId = newParentId;
  updateSubtreeLevel(
    location.node,
    newParent ? (newParent.node.levelNo ?? 1) + 1 : 1,
  );
  targetSiblings.splice(insertionIndex, 0, location.node);
  normalizeSiblingOrder(targetSiblings);
  return location.node;
}

export function normalizeSiblingOrder(siblings: ChainNode[]) {
  siblings.forEach((node, index) => {
    node.sortNo = index;
  });
}

export function updateSubtreeLevel(node: ChainNode, levelNo: number) {
  node.levelNo = levelNo;
  for (const child of node.children ?? []) {
    child.parentId = node.id;
    updateSubtreeLevel(child, levelNo + 1);
  }
}

function findNodeInSiblings(
  chainId: number,
  siblings: ChainNode[],
  nodeId: number,
  parentId: number | null,
): NodeLocation | undefined {
  for (const node of siblings) {
    if (node.id === nodeId) {
      return { chainId, node, parentId, siblings };
    }
    const childLocation = findNodeInSiblings(
      chainId,
      node.children ?? [],
      nodeId,
      node.id,
    );
    if (childLocation) return childLocation;
  }
  return undefined;
}
