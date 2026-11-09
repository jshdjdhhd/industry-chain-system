import { describe, expect, it } from "vitest";
import type { ChainNode } from "@/types/domain";
import {
  buildSortRequest,
  collectDescendantIds,
  countSubtreeNodes,
  findNodeLocation,
  flattenNodeTree,
  getSiblingGroup,
  getSiblingNodes,
  getValidParentOptions,
  insertSibling,
  moveNodeInTree,
  moveSibling,
} from "./nodeTreeRules";

const tree: ChainNode[] = [
  {
    id: 1,
    name: "根节点 A",
    children: [
      {
        id: 2,
        name: "子节点 A1",
        levelNo: 2,
        parentId: 1,
        children: [{ id: 3, name: "孙节点 A11", levelNo: 3, parentId: 2, children: [] }],
      },
      { id: 4, name: "子节点 A2", levelNo: 2, parentId: 1, children: [] },
    ],
  },
  { id: 5, name: "根节点 B", levelNo: 1, parentId: null, children: [] },
];

function cloneTree() {
  return structuredClone(tree);
}

describe("nodeTreeRules", () => {
  it("derives parent, position, level and display path from tree nesting", () => {
    expect(
      flattenNodeTree(tree).map(({ id, parentId, position, level, pathName }) => ({
        id,
        parentId,
        position,
        level,
        pathName,
      })),
    ).toEqual([
      { id: 1, parentId: null, position: 0, level: 1, pathName: "根节点 A" },
      { id: 2, parentId: 1, position: 0, level: 2, pathName: "根节点 A / 子节点 A1" },
      { id: 3, parentId: 2, position: 0, level: 3, pathName: "根节点 A / 子节点 A1 / 孙节点 A11" },
      { id: 4, parentId: 1, position: 1, level: 2, pathName: "根节点 A / 子节点 A2" },
      { id: 5, parentId: null, position: 1, level: 1, pathName: "根节点 B" },
    ]);
  });

  it("collects descendants and excludes the selected subtree from parent options", () => {
    expect(collectDescendantIds(tree, 1)).toEqual([2, 3, 4]);
    expect(getValidParentOptions(tree, 1).map((entry) => entry.id)).toEqual([5]);
    expect(getValidParentOptions(tree, 2).map((entry) => entry.id)).toEqual([1, 4, 5]);
  });

  it("builds sibling orders without crossing first or last boundaries", () => {
    const siblings = getSiblingNodes(tree, 1);
    expect(moveSibling(siblings, 2, "up").map((node) => node.id)).toEqual([2, 4]);
    expect(moveSibling(siblings, 2, "down").map((node) => node.id)).toEqual([4, 2]);
    expect(insertSibling(siblings, 4, 0).map((node) => node.id)).toEqual([4, 2]);
    expect(buildSortRequest(1, insertSibling(siblings, 4, 0))).toEqual({
      parentId: 1,
      nodeOrders: [
        { nodeId: 4, sortOrder: 0 },
        { nodeId: 2, sortOrder: 1 },
      ],
    });
  });

  it("locates nodes and sibling groups in a chain-indexed tree map", () => {
    const nodeTrees: Record<number, ChainNode[]> = { 7: cloneTree() };
    expect(findNodeLocation(nodeTrees, 3)).toMatchObject({ chainId: 7, parentId: 2 });
    expect(getSiblingGroup(nodeTrees, 7, null)?.map((node) => node.id)).toEqual([1, 5]);
    expect(getSiblingGroup(nodeTrees, 7, 1)?.map((node) => node.id)).toEqual([2, 4]);
  });

  it("moves a subtree, updates level numbers and normalizes sibling order for mock adapter", () => {
    const nodeTrees: Record<number, ChainNode[]> = { 7: cloneTree() };

    const moved = moveNodeInTree(nodeTrees, 2, 5, 0);

    expect(moved.parentId).toBe(5);
    expect(moved.levelNo).toBe(2);
    expect(moved.children?.[0].parentId).toBe(2);
    expect(moved.children?.[0].levelNo).toBe(3);
    expect(getSiblingGroup(nodeTrees, 7, 1)?.map((node) => node.id)).toEqual([4]);
    expect(getSiblingGroup(nodeTrees, 7, 5)?.map((node) => node.id)).toEqual([2]);
    expect(getSiblingGroup(nodeTrees, 7, 5)?.[0].sortNo).toBe(0);
  });

  it("rejects moving a node into its own descendant", () => {
    const nodeTrees: Record<number, ChainNode[]> = { 7: cloneTree() };

    expect(() => moveNodeInTree(nodeTrees, 2, 3, 0)).toThrow("节点不能移动到自己的子节点下");
  });

  it("counts the selected node and every node in its subtree", () => {
    expect(countSubtreeNodes(tree[0])).toBe(4);
    expect(countSubtreeNodes(tree[1])).toBe(1);
  });
});
