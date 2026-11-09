import { describe, expect, it } from "vitest";
import type { ChainNode } from "@/types/domain";
import {
  buildSortRequest,
  collectDescendantIds,
  countSubtreeNodes,
  flattenNodeTree,
  getSiblingNodes,
  getValidParentOptions,
  insertSibling,
  moveSibling,
} from "./nodeAdjustmentViewModel";

const tree: ChainNode[] = [
  {
    id: 1,
    name: "根节点 A",
    children: [
      {
        id: 2,
        name: "子节点 A1",
        children: [{ id: 3, name: "孙节点 A11", children: [] }],
      },
      { id: 4, name: "子节点 A2", children: [] },
    ],
  },
  { id: 5, name: "根节点 B", children: [] },
];

describe("nodeAdjustmentViewModel", () => {
  it("derives parent, position, level and display path from tree nesting", () => {
    expect(
      flattenNodeTree(tree).map(
        ({ id, parentId, position, level, pathName }) => ({
          id,
          parentId,
          position,
          level,
          pathName,
        }),
      ),
    ).toEqual([
      {
        id: 1,
        parentId: null,
        position: 0,
        level: 1,
        pathName: "根节点 A",
      },
      {
        id: 2,
        parentId: 1,
        position: 0,
        level: 2,
        pathName: "根节点 A / 子节点 A1",
      },
      {
        id: 3,
        parentId: 2,
        position: 0,
        level: 3,
        pathName: "根节点 A / 子节点 A1 / 孙节点 A11",
      },
      {
        id: 4,
        parentId: 1,
        position: 1,
        level: 2,
        pathName: "根节点 A / 子节点 A2",
      },
      {
        id: 5,
        parentId: null,
        position: 1,
        level: 1,
        pathName: "根节点 B",
      },
    ]);
  });

  it("collects all descendants and excludes the selected subtree from parent options", () => {
    expect(collectDescendantIds(tree, 1)).toEqual([2, 3, 4]);
    expect(getValidParentOptions(tree, 1).map((entry) => entry.id)).toEqual([
      5,
    ]);
    expect(getValidParentOptions(tree, 2).map((entry) => entry.id)).toEqual([
      1, 4, 5,
    ]);
  });

  it("returns root and ordinary sibling groups from derived parent relationships", () => {
    expect(getSiblingNodes(tree, null).map((node) => node.id)).toEqual([1, 5]);
    expect(getSiblingNodes(tree, 1).map((node) => node.id)).toEqual([2, 4]);
  });

  it("moves siblings without crossing first or last boundaries", () => {
    const siblings = getSiblingNodes(tree, 1);
    expect(moveSibling(siblings, 2, "up").map((node) => node.id)).toEqual([
      2, 4,
    ]);
    expect(moveSibling(siblings, 2, "down").map((node) => node.id)).toEqual([
      4, 2,
    ]);
    expect(moveSibling(siblings, 4, "down").map((node) => node.id)).toEqual([
      2, 4,
    ]);
  });

  it("inserts a node at the requested position and generates zero-based orders", () => {
    const reordered = insertSibling(getSiblingNodes(tree, 1), 4, 0);
    expect(reordered.map((node) => node.id)).toEqual([4, 2]);
    expect(buildSortRequest(1, reordered)).toEqual({
      parentId: 1,
      nodeOrders: [
        { nodeId: 4, sortOrder: 0 },
        { nodeId: 2, sortOrder: 1 },
      ],
    });
  });

  it("counts the selected node and every node in its subtree", () => {
    expect(countSubtreeNodes(tree[0])).toBe(4);
    expect(countSubtreeNodes(tree[1])).toBe(1);
  });
});
