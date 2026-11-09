import { describe, expect, it, vi } from "vitest";
import { get, post, put } from "./http";
import { mockApi } from "./mock";
import {
  batchLinkEnterprisesToNode,
  fetchChainNodeCandidates,
  fetchChainOptions,
  mapBackendChainPage,
  mapBackendNodeEnterpriseList,
  mapBackendNodePage,
  mapBackendNodeTree,
  moveNode,
  searchNodesByKeyword,
  sortNodes,
  toBackendChainPayload,
  toBackendNodePayload,
  updateNode,
} from "./chain";

vi.mock("./http", () => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  remove: vi.fn(),
}));

describe("chain API backend contract adapters", () => {
  it("fetches chain options from /chains/options", async () => {
    vi.mocked(get).mockResolvedValue([{ id: 1, name: "锂电池产业链" }]);

    const result = await fetchChainOptions();

    expect(get).toHaveBeenCalledWith("/chains/options");
    expect(result).toEqual([{ id: 1, name: "锂电池产业链" }]);
  });

  it("fetches node candidates from /chains/{id}/node-options", async () => {
    vi.mocked(get).mockResolvedValue([]);

    await fetchChainNodeCandidates(1);

    expect(get).toHaveBeenCalledWith("/chains/1/node-options");
  });

  it("maps backend paged chain data to the front-end chain list", () => {
    expect(
      mapBackendChainPage({
        records: [
          {
            id: 1,
            chainName: "New Energy Chain",
            description: "demo",
            nodeCount: 4,
            enterpriseCount: 3,
            createTime: "2026-06-25 23:13:12",
          },
        ],
        total: 1,
        pageNum: 1,
        pageSize: 10,
      }),
    ).toEqual([
      {
        id: 1,
        name: "New Energy Chain",
        description: "demo",
        nodeCount: 4,
        enterpriseCount: 3,
      },
    ]);
  });

  it("maps front-end chain payloads to backend chain DTOs", () => {
    expect(
      toBackendChainPayload({
        name: "Equipment Chain",
        description: "equipment",
        sortNo: 9,
      }),
    ).toEqual({
      chainName: "Equipment Chain",
      description: "equipment",
    });
  });

  it("unwraps the backend synthetic tree root and maps node fields", () => {
    expect(
      mapBackendNodeTree({
        id: 0,
        nodeName: "root",
        nodeLevel: 0,
        directEnterpriseCount: 0,
        subtreeEnterpriseCount: 3,
        children: [
          {
            id: 10,
            nodeName: "Battery Cell",
            stageType: "UPSTREAM",
            nodeLevel: 1,
            parentId: null,
            sortOrder: 1,
            keywords: "battery,lithium",
            description: "Battery cell manufacturing",
            directEnterpriseCount: 2,
            subtreeEnterpriseCount: 3,
            children: [
              {
                id: 11,
                nodeName: "Separator",
                nodeLevel: 2,
                parentId: 10,
                sortOrder: 7,
                keywords: "separator,membrane",
                description: "Battery separator manufacturing",
                directEnterpriseCount: 1,
                subtreeEnterpriseCount: 1,
                children: [],
              },
            ],
          },
        ],
      }),
    ).toEqual([
      {
        id: 10,
        name: "Battery Cell",
        stageType: "UPSTREAM",
        levelNo: 1,
        parentId: null,
        sortNo: 1,
        keywords: "battery,lithium",
        definition: "Battery cell manufacturing",
        directEnterpriseCount: 2,
        subtreeEnterpriseCount: 3,
        children: [
          {
            id: 11,
            name: "Separator",
            stageType: null,
            levelNo: 2,
            parentId: 10,
            sortNo: 7,
            keywords: "separator,membrane",
            definition: "Battery separator manufacturing",
            directEnterpriseCount: 1,
            subtreeEnterpriseCount: 1,
            children: [],
          },
        ],
      },
    ]);
  });

  it("maps front-end node payloads to backend node DTOs", () => {
    expect(
      toBackendNodePayload({
        parentId: 10,
        name: "Cathode Material",
        definition: "Material segment",
        keywords: "lithium,nickel",
        sortNo: 3,
      }),
    ).toEqual({
      parentId: 10,
      nodeName: "Cathode Material",
      keywords: "lithium,nickel",
      description: "Material segment",
      sortOrder: 3,
    });
  });

  it("maps backend paged nodes and preserves knowledge and pagination fields", () => {
    expect(
      mapBackendNodePage({
        records: [
          {
            id: 12,
            chainId: 2,
            parentId: 10,
            nodeName: "Cathode Material",
            stageType: "UPSTREAM",
            nodeLevel: 2,
            nodePath: "/Battery/Cathode",
            keywords: "lithium,nickel",
            description: "Battery cathode materials",
            sortOrder: 3,
            createTime: "2026-07-01 10:00:00",
          },
        ],
        total: 21,
        pageNum: 2,
        pageSize: 10,
      }),
    ).toEqual({
      records: [
        {
          id: 12,
          chainId: 2,
          parentId: 10,
          name: "Cathode Material",
          stageType: "UPSTREAM",
          levelNo: 2,
          nodePath: "/Battery/Cathode",
          keywords: "lithium,nickel",
          definition: "Battery cathode materials",
          sortNo: 3,
          children: [],
        },
      ],
      total: 21,
      pageNum: 2,
      pageSize: 10,
    });
  });

  it("searches backend nodes by keyword with defaults and preserves the page result", async () => {
    vi.mocked(get).mockResolvedValue({
      records: [
        {
          id: 12,
          chainId: 2,
          parentId: 10,
          nodeName: "Cathode Material",
          keywords: "lithium,nickel",
          description: "Battery cathode materials",
        },
      ],
      total: 1,
      pageNum: 1,
      pageSize: 20,
    });

    await expect(
      searchNodesByKeyword({ keyword: "lithium", chainId: 2 }),
    ).resolves.toMatchObject({
      records: [
        {
          id: 12,
          name: "Cathode Material",
          stageType: null,
          keywords: "lithium,nickel",
          definition: "Battery cathode materials",
        },
      ],
      total: 1,
      pageNum: 1,
      pageSize: 20,
    });

    expect(get).toHaveBeenCalledWith("/chains/nodes/search-by-keyword", {
      params: {
        keyword: "lithium",
        chainId: 2,
        pageNum: 1,
        pageSize: 20,
      },
    });
  });

  it("wraps backend node enterprise relations in the front-end result shape", () => {
    expect(
      mapBackendNodeEnterpriseList(10, [
        {
          enterpriseId: 7,
          enterpriseName: "比亚迪股份有限公司",
          source: "MANUAL",
          createTime: "2026-06-26 10:40:03",
        },
      ]),
    ).toEqual({
      nodeId: 10,
      directEnterpriseCount: 1,
      subtreeEnterpriseCount: 1,
      records: [
        {
          enterpriseId: 7,
          enterpriseName: "比亚迪股份有限公司",
          source: "MANUAL",
          createdAt: "2026-06-26 10:40:03",
        },
      ],
    });
  });

  it("posts batch enterprise links with the backend field name", async () => {
    const batchResult = {
      total: 2,
      linkedCount: 2,
      skippedCount: 0,
      failedCount: 0,
      items: [
        { enterpriseId: 7, status: "LINKED", message: "linked" },
        { enterpriseId: 8, status: "LINKED", message: "linked" },
      ],
    };
    vi.mocked(post).mockResolvedValue(batchResult);

    await expect(batchLinkEnterprisesToNode(10, [7, 8])).resolves.toEqual(
      batchResult,
    );

    expect(post).toHaveBeenCalledWith("/nodes/10/enterprises/batch", {
      enterpriseIds: [7, 8],
    });
  });

  it("moves a node with the backend path and preserves a null parent", async () => {
    vi.mocked(put).mockResolvedValue({
      id: 12,
      chainId: 2,
      parentId: null,
      nodeName: "Cathode Material",
      nodeLevel: 1,
      nodePath: "/12/",
      sortOrder: 0,
    });

    await expect(
      moveNode(12, { newParentId: null, sortOrder: 0 }),
    ).resolves.toMatchObject({
      id: 12,
      parentId: null,
      name: "Cathode Material",
      levelNo: 1,
      sortNo: 0,
    });

    expect(put).toHaveBeenCalledWith("/chains/nodes/12/move", {
      newParentId: null,
      sortOrder: 0,
    });
  });

  it("updates node fields and parent atomically", async () => {
    vi.mocked(put).mockResolvedValue({
      id: 12,
      chainId: 1,
      parentId: 20,
      nodeName: "动力电池",
      nodeLevel: 2,
      nodePath: "/20/12/",
      sortOrder: 3,
    });

    await updateNode(12, {
      parentId: 20,
      parentChangeRequested: true,
      name: "动力电池",
      keywords: "动力电池,电芯",
      definition: "电池制造",
      sortNo: 3,
    });

    expect(put).toHaveBeenCalledWith("/chains/nodes/12", {
      parentId: 20,
      parentChangeRequested: true,
      nodeName: "动力电池",
      keywords: "动力电池,电芯",
      description: "电池制造",
      sortOrder: 3,
    });
  });

  it("submits the complete sibling order and handles a void response", async () => {
    vi.mocked(put).mockResolvedValue(undefined);

    const request = {
      parentId: 10,
      nodeOrders: [
        { nodeId: 12, sortOrder: 0 },
        { nodeId: 13, sortOrder: 1 },
      ],
    };

    await expect(sortNodes(request)).resolves.toBeUndefined();
    expect(put).toHaveBeenCalledWith("/chains/nodes/sort", request);
  });

  it("creates and protects three stage nodes in mock mode", async () => {
    const chain = await mockApi.createChain({ name: "Mock Stage Chain" });
    const tree = await mockApi.fetchNodeTree(chain.id);

    expect(chain.nodeCount).toBe(3);
    expect(tree.map((node) => node.stageType)).toEqual([
      "UPSTREAM",
      "MIDSTREAM",
      "DOWNSTREAM",
    ]);
    await expect(
      mockApi.moveNode(tree[0].id, { newParentId: null }),
    ).rejects.toThrow("阶段节点无法修改父节点");
    await expect(mockApi.deleteNode(tree[0].id)).rejects.toThrow(
      "上下游阶段节点不允许删除",
    );
  });
  it("returns item-level batch link statuses in mock mode", async () => {
    const chain = await mockApi.createChain({ name: "Mock Batch Chain" });
    const node = await mockApi.createNode(chain.id, { name: "Batch Node" });
    const firstEnterprise = await mockApi.createEnterprise({
      name: "Batch Enterprise A",
    });
    const secondEnterprise = await mockApi.createEnterprise({
      name: "Batch Enterprise B",
    });
    await mockApi.inboundEnterprise(firstEnterprise.id);
    await mockApi.inboundEnterprise(secondEnterprise.id);
    await mockApi.linkEnterpriseToNode(node.id, firstEnterprise.id);

    const result = await mockApi.batchLinkEnterprisesToNode(node.id, [
      firstEnterprise.id,
      secondEnterprise.id,
      secondEnterprise.id,
    ]);

    expect(result).toMatchObject({
      total: 3,
      linkedCount: 1,
      skippedCount: 2,
      failedCount: 0,
    });
    expect(result.items.map((item) => item.status)).toEqual([
      "ALREADY_LINKED",
      "LINKED",
      "DUPLICATE_IN_REQUEST",
    ]);
  });

  it("searches mock nodes by keyword and returns correct pagination fields", async () => {
    const uniqueKeyword = "knowledge-field-search-20260701";
    const chain = await mockApi.createChain({
      name: "Mock Knowledge Search Chain",
    });
    const matchingNode = await mockApi.createNode(chain.id, {
      name: "Matching Knowledge Node",
      keywords: `energy,${uniqueKeyword}`,
      definition: "Knowledge-rich node",
    });
    await mockApi.createNode(chain.id, {
      name: "Non-matching Knowledge Node",
      keywords: "unrelated",
      definition: "Other node",
    });

    await expect(
      mockApi.searchNodesByKeyword(uniqueKeyword, chain.id, 1, 5),
    ).resolves.toEqual({
      records: [matchingNode],
      total: 1,
      pageNum: 1,
      pageSize: 5,
    });
  });

  it("moves a mock subtree and keeps descendants under the moved node", async () => {
    const firstChain = await mockApi.createChain({
      name: "Mock Move Chain",
    });
    const secondChain = await mockApi.createChain({
      name: "Mock Other Chain",
    });
    const firstRoot = await mockApi.createNode(firstChain.id, {
      name: "First Root",
    });
    const secondRoot = await mockApi.createNode(firstChain.id, {
      name: "Second Root",
    });
    const child = await mockApi.createNode(firstChain.id, {
      parentId: firstRoot.id,
      name: "Child",
    });
    const grandchild = await mockApi.createNode(firstChain.id, {
      parentId: child.id,
      name: "Grandchild",
    });
    const otherChainRoot = await mockApi.createNode(secondChain.id, {
      name: "Other Chain Root",
    });

    await expect(
      mockApi.moveNode(child.id, {
        newParentId: secondRoot.id,
        sortOrder: 0,
      }),
    ).resolves.toMatchObject({
      id: child.id,
      parentId: secondRoot.id,
      levelNo: 2,
    });

    const tree = await mockApi.fetchNodeTree(firstChain.id);
    const movedChild = tree
      .find((node) => node.id === secondRoot.id)
      ?.children?.find((node) => node.id === child.id);
    expect(movedChild?.children?.map((node) => node.id)).toEqual([
      grandchild.id,
    ]);

    await expect(
      mockApi.moveNode(secondRoot.id, {
        newParentId: grandchild.id,
      }),
    ).rejects.toThrow("节点不能移动到自己的子节点下");
    await expect(
      mockApi.moveNode(secondRoot.id, {
        newParentId: otherChainRoot.id,
      }),
    ).rejects.toThrow("不能跨产业链移动节点");
  });

  it("sorts only the requested mock sibling group", async () => {
    const chain = await mockApi.createChain({ name: "Mock Sort Chain" });
    const parent = await mockApi.createNode(chain.id, { name: "Parent" });
    const first = await mockApi.createNode(chain.id, {
      parentId: parent.id,
      name: "First",
    });
    const second = await mockApi.createNode(chain.id, {
      parentId: parent.id,
      name: "Second",
    });
    const rootSibling = await mockApi.createNode(chain.id, {
      name: "Root Sibling",
    });

    await mockApi.sortNodes({
      parentId: parent.id,
      nodeOrders: [
        { nodeId: second.id, sortOrder: 0 },
        { nodeId: first.id, sortOrder: 1 },
      ],
    });

    const tree = await mockApi.fetchNodeTree(chain.id);
    expect(
      tree
        .find((node) => node.id === parent.id)
        ?.children?.map((node) => node.id),
    ).toEqual([second.id, first.id]);
    expect(tree.some((node) => node.id === rootSibling.id)).toBe(true);
  });

  it("updates mock node fields and parent in one request", async () => {
    const chain = await mockApi.createChain({
      name: "Mock Atomic Update Chain",
    });
    const source = await mockApi.createNode(chain.id, { name: "Source" });
    const target = await mockApi.createNode(chain.id, { name: "Target" });
    const child = await mockApi.createNode(chain.id, {
      parentId: source.id,
      name: "Child",
    });

    await mockApi.updateNode(child.id, {
      parentId: target.id,
      parentChangeRequested: true,
      name: "Moved Child",
      sortNo: 0,
    });

    const tree = await mockApi.fetchNodeTree(chain.id);
    expect(
      tree
        .find((node) => node.id === target.id)
        ?.children?.map((node) => node.id),
    ).toContain(child.id);
  });

  it("rejects parent edits for mock stage nodes", async () => {
    const chain = await mockApi.createChain({
      name: "Mock Protected Stage Chain",
    });
    const tree = await mockApi.fetchNodeTree(chain.id);

    await expect(
      mockApi.updateNode(tree[0].id, {
        parentId: tree[1].id,
        parentChangeRequested: true,
        name: tree[0].name,
      }),
    ).rejects.toThrow("阶段节点无法修改父节点");
  });
});
