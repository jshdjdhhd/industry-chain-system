import { describe, expect, it } from "vitest";
import type { DiagnosisTreeNode } from "@/types/domain";
import {
  buildNodeSubLabel,
  collectSelectedNodeIds,
  findG6ModelByNodeId,
  isAllFirstLevelSelected,
  resolveHealthLabel,
  resolveHealthLevel,
  resolveHealthScore,
  resolveNodePalette,
  resolveNodeSize,
  toG6DiagnosisModel,
  toG6TreeData,
  toggleSelectedNodeId,
} from "./chainDiagnosisViewModel";

function makeNode(
  overrides: Partial<DiagnosisTreeNode> = {},
): DiagnosisTreeNode {
  return {
    nodeId: 1,
    nodeName: "节点",
    nodeLevel: 1,
    directEnterpriseCount: 0,
    subtreeEnterpriseCount: 0,
    children: [],
    ...overrides,
  };
}

describe("resolveNodeSize", () => {
  it("returns 24 when subtree has no enterprises", () => {
    expect(resolveNodeSize(80, 0)).toBe(24);
  });

  it("clamps to [28, 64] based on health score", () => {
    expect(resolveNodeSize(0, 5)).toBe(28);
    expect(resolveNodeSize(80, 5)).toBe(64);
    expect(resolveNodeSize(50, 5)).toBe(49);
    expect(resolveNodeSize(100, 5)).toBe(64);
  });
});

describe("resolveNodePalette", () => {
  it("maps each health level to its fill/stroke pair", () => {
    expect(resolveNodePalette("HEALTHY")).toEqual({
      fill: "#16a34a",
      stroke: "#15803d",
    });
    expect(resolveNodePalette("NORMAL")).toEqual({
      fill: "#d9a90f",
      stroke: "#a16207",
    });
    expect(resolveNodePalette("WEAK")).toEqual({
      fill: "#f97316",
      stroke: "#c2410c",
    });
    expect(resolveNodePalette("RISK")).toEqual({
      fill: "#dc2626",
      stroke: "#991b1b",
    });
    expect(resolveNodePalette("EMPTY")).toEqual({
      fill: "#dc2626",
      stroke: "#7f1d1d",
    });
  });

  it("falls back to neutral grey for unknown/missing level", () => {
    expect(resolveNodePalette(undefined)).toEqual({
      fill: "#94a3b8",
      stroke: "#475467",
    });
    expect(resolveNodePalette("UNKNOWN")).toEqual({
      fill: "#94a3b8",
      stroke: "#475467",
    });
  });
});

describe("resolveHealthScore", () => {
  it("returns backend score when provided", () => {
    expect(resolveHealthScore(72, 3)).toBe(72);
  });

  it("estimates from subtree count when score missing", () => {
    expect(resolveHealthScore(null, 0)).toBe(0);
    expect(resolveHealthScore(null, 3)).toBe(27);
    expect(resolveHealthScore(null, 10)).toBe(90);
    expect(resolveHealthScore(null, 20)).toBe(90);
  });
});

describe("resolveHealthLevel", () => {
  it("returns backend level when provided", () => {
    expect(resolveHealthLevel("HEALTHY", 0)).toBe("HEALTHY");
  });

  it("estimates level from subtree count", () => {
    expect(resolveHealthLevel(undefined, 0)).toBe("EMPTY");
    expect(resolveHealthLevel(undefined, 2)).toBe("RISK");
    expect(resolveHealthLevel(undefined, 5)).toBe("WEAK");
    expect(resolveHealthLevel(undefined, 10)).toBe("NORMAL");
    expect(resolveHealthLevel(undefined, 11)).toBe("HEALTHY");
  });
});

describe("resolveHealthLabel", () => {
  it("prefers backend label", () => {
    expect(resolveHealthLabel("强健", "HEALTHY")).toBe("强健");
  });

  it("falls back to level-based Chinese label", () => {
    expect(resolveHealthLabel(null, "HEALTHY")).toBe("健康");
    expect(resolveHealthLabel(null, "EMPTY")).toBe("空白");
  });
});

describe("buildNodeSubLabel", () => {
  it("combines health label and subtree enterprise count", () => {
    expect(buildNodeSubLabel("健康", 12)).toBe("健康 · 12 家");
  });
});

describe("toG6DiagnosisModel", () => {
  it("maps a leaf node with backend health fields", () => {
    const node = makeNode({
      nodeId: 10,
      nodeName: "电池",
      nodeLevel: 1,
      directEnterpriseCount: 3,
      subtreeEnterpriseCount: 8,
      healthScore: 70,
      healthLevel: "NORMAL",
      healthLabel: "一般",
      keywords: "battery",
      description: "电池制造",
      weakReasons: ["产能不足"],
    });
    const model = toG6DiagnosisModel(node);
    expect(model.id).toBe("node-10");
    expect(model.label).toBe("电池");
    expect(model.subLabel).toBe("一般 · 8 家");
    expect(model.healthLevel).toBe("NORMAL");
    expect(model.fill).toBe("#d9a90f");
    expect(model.stroke).toBe("#a16207");
    expect(model.size).toBe(59);
    expect(model.collapsed).toBe(false);
    expect(model.children).toEqual([]);
    expect(model.weakReasons).toEqual(["产能不足"]);
  });

  it("maps a tree and collapses nodes at or beyond collapsedDepth", () => {
    const root = makeNode({
      nodeId: 0,
      nodeName: "产业链",
      nodeLevel: 0,
      subtreeEnterpriseCount: 20,
      healthLevel: "HEALTHY",
      children: [
        makeNode({
          nodeId: 1,
          nodeName: "一级A",
          nodeLevel: 1,
          subtreeEnterpriseCount: 10,
          healthLevel: "NORMAL",
          children: [
            makeNode({
              nodeId: 11,
              nodeName: "二级A",
              nodeLevel: 2,
              subtreeEnterpriseCount: 3,
              healthLevel: "WEAK",
              children: [
                makeNode({
                  nodeId: 111,
                  nodeName: "三级A",
                  nodeLevel: 3,
                  subtreeEnterpriseCount: 1,
                  healthLevel: "RISK",
                }),
              ],
            }),
          ],
        }),
        makeNode({
          nodeId: 2,
          nodeName: "一级B",
          nodeLevel: 1,
          subtreeEnterpriseCount: 0,
          healthLevel: "EMPTY",
        }),
      ],
    });

    const model = toG6DiagnosisModel(root, { collapsedDepth: 2 });
    expect(model.collapsed).toBe(false); // level 0
    expect(model.children[0].collapsed).toBe(false); // level 1
    expect(model.children[0].children[0].collapsed).toBe(true); // level 2
    expect(model.children[1].collapsed).toBe(false); // leaf, no children
  });

  it("toG6TreeData returns root model directly", () => {
    const root = makeNode({ nodeId: 0, nodeName: "根" });
    expect(toG6TreeData(root).nodeId).toBe(0);
  });
});

describe("toggleSelectedNodeId", () => {
  it("adds a node id when absent", () => {
    const next = toggleSelectedNodeId(new Set([1]), 2);
    expect(Array.from(next).sort()).toEqual([1, 2]);
  });

  it("removes a node id when present", () => {
    const next = toggleSelectedNodeId(new Set([1, 2]), 2);
    expect(Array.from(next).sort()).toEqual([1]);
  });

  it("does not mutate the input set", () => {
    const original = new Set([1]);
    const next = toggleSelectedNodeId(original, 2);
    expect(original.has(2)).toBe(false);
    expect(next.has(2)).toBe(true);
  });
});

describe("isAllFirstLevelSelected", () => {
  const root = makeNode({
    nodeId: 0,
    children: [makeNode({ nodeId: 1 }), makeNode({ nodeId: 2 })],
  });

  it("returns false when some first-level nodes are unselected", () => {
    expect(isAllFirstLevelSelected(root, new Set([1]))).toBe(false);
  });

  it("returns true when all first-level nodes are selected", () => {
    expect(isAllFirstLevelSelected(root, new Set([1, 2]))).toBe(true);
  });

  it("returns false when root has no children", () => {
    expect(isAllFirstLevelSelected(makeNode(), new Set())).toBe(false);
  });
});

describe("collectSelectedNodeIds", () => {
  it("returns sorted node id array", () => {
    expect(collectSelectedNodeIds(new Set([3, 1, 2]))).toEqual([1, 2, 3]);
  });
});

describe("findG6ModelByNodeId", () => {
  const root = toG6DiagnosisModel(
    makeNode({
      nodeId: 0,
      children: [makeNode({ nodeId: 1, children: [makeNode({ nodeId: 11 })] })],
    }),
  );

  it("finds the root", () => {
    expect(findG6ModelByNodeId(root, 0)?.nodeId).toBe(0);
  });

  it("finds a nested node", () => {
    expect(findG6ModelByNodeId(root, 11)?.nodeId).toBe(11);
  });

  it("returns undefined when not found", () => {
    expect(findG6ModelByNodeId(root, 99)).toBeUndefined();
  });
});
