import {
  findNodeLocation,
  getDescendantNodeIds,
  getSiblingGroup,
  moveNodeInTree,
  normalizeSiblingOrder,
} from "@/domain/nodeTreeRules";
import type {
  AnalysisReportDetail,
  AnalysisReportTask,
  BatchLinkEnterpriseItem,
  BatchLinkEnterpriseResult,
  ChainNode,
  ChainNodeCandidate,
  ChainNodePayload,
  ChainOption,
  ChainPayload,
  ChainStageType,
  DashboardOverview,
  DiagnosisTreeNode,
  EnterpriseDetail,
  EnterpriseImportRecord,
  EnterpriseOption,
  EnterprisePayload,
  EnterpriseProfileAnalysis,
  EnterpriseRadar,
  IndustryChain,
  MatchingTask,
  MoveNodeRequest,
  NodeEnterprise,
  NodeEnterpriseResult,
  PageResult,
  SortNodesRequest,
  Tag,
  UpdateChainNodePayload,
} from "@/types/domain";
import type {
  EnterpriseQuery,
  ImportRecordQuery,
  ImportResult,
} from "./enterprise";
import type { TagPayload } from "./tag";
import type {
  AnalysisReportQuery,
  CreateAnalysisReportRequest,
  AnalysisReportDownloadFormat,
} from "./analysis";

let nextEnterpriseId = 1;
let nextTagId = 1;
let nextChainId = 1;
let nextNodeId = 1;
let nextTaskId = 1;
let nextReportId = 5001;

const tags: Tag[] = [];
const enterprises: EnterpriseDetail[] = [];
const importRecords: EnterpriseImportRecord[] = [];
const chains: IndustryChain[] = [];
const nodeTrees: Record<number, ChainNode[]> = {};

const DEFAULT_CHAIN_STAGES: ReadonlyArray<{
  stageType: ChainStageType;
  name: string;
  sortNo: number;
}> = [
  { stageType: "UPSTREAM", name: "上游", sortNo: 1 },
  { stageType: "MIDSTREAM", name: "中游", sortNo: 2 },
  { stageType: "DOWNSTREAM", name: "下游", sortNo: 3 },
];

function createDefaultStageNodes(chainId: number): ChainNode[] {
  return DEFAULT_CHAIN_STAGES.map((stage) => {
    const id = nextNodeId++;
    return {
      id,
      chainId,
      parentId: null,
      name: stage.name,
      stageType: stage.stageType,
      levelNo: 1,
      nodePath: `/${id}/`,
      sortNo: stage.sortNo,
      status: "ENABLED",
      directEnterpriseCount: 0,
      subtreeEnterpriseCount: 0,
      children: [],
    };
  });
}
let matchingTask: MatchingTask = {
  taskId: 9001,
  enterpriseId: 0,
  chainId: 0,
  status: "COMPLETED",
  results: [],
};
let matchingTaskPollCount = 0;

const mockReports: AnalysisReportTask[] = [
  {
    id: 5001,
    chainId: 1,
    chainName: "新能源汽车产业链",
    reportType: "HEALTH_ANALYSIS",
    scopeType: "ALL_CHAIN",
    selectedNodeIds: [],
    resolvedNodeIds: [],
    status: "COMPLETED",
    retryCount: 0,
    createdBy: "admin",
    createdAt: "2026-07-01 10:00:00",
    updatedAt: "2026-07-01 10:01:30",
    completedAt: "2026-07-01 10:01:30",
  },
  {
    id: 5002,
    chainId: 1,
    chainName: "新能源汽车产业链",
    reportType: "HEALTH_ANALYSIS",
    scopeType: "SELECTED_NODES",
    selectedNodeIds: [10, 11],
    resolvedNodeIds: [10, 11, 12],
    status: "FAILED",
    retryCount: 0,
    errorMessage: "大模型调用超时",
    createdBy: "admin",
    createdAt: "2026-07-02 09:20:00",
    updatedAt: "2026-07-02 09:22:00",
    completedAt: null,
  },
];
const mockReportPollCount: Record<number, number> = {};

function sleep<T>(data: T): Promise<T> {
  return new Promise((resolve) =>
    window.setTimeout(() => resolve(structuredClone(data)), 180),
  );
}

function rejectMock(message: string): Promise<never> {
  return Promise.reject(new Error(message));
}

function paginate<T>(records: T[], pageNum = 1, pageSize = 10): PageResult<T> {
  const start = (pageNum - 1) * pageSize;
  return {
    records: records.slice(start, start + pageSize),
    total: records.length,
    pageNum,
    pageSize,
  };
}

function flattenNodes(nodes: ChainNode[]): ChainNode[] {
  return nodes.flatMap((node) => [node, ...flattenNodes(node.children ?? [])]);
}

function removeNodeById(nodes: ChainNode[], id: number): ChainNode[] {
  return nodes
    .filter((node) => node.id !== id)
    .map((node) => ({
      ...node,
      children: removeNodeById(node.children ?? [], id),
    }));
}

function getNodeToChainMap() {
  const entries = Object.entries(nodeTrees).flatMap(([chainId, nodes]) =>
    flattenNodes(nodes).map((node) => [node.id, Number(chainId)] as const),
  );
  return new Map(entries);
}

function countDirectEnterprises(nodeId: number) {
  return enterprises.filter((enterprise) =>
    enterprise.linkedNodes?.some((linkedNode) => linkedNode.nodeId === nodeId),
  ).length;
}

function countSubtreeEnterprises(node: ChainNode) {
  const nodeIds = new Set([node.id, ...getDescendantNodeIds(node)]);
  return enterprises.filter((enterprise) =>
    enterprise.linkedNodes?.some((linkedNode) =>
      nodeIds.has(linkedNode.nodeId),
    ),
  ).length;
}

function withComputedNodeCounts(nodes: ChainNode[]): ChainNode[] {
  return nodes.map((node) => {
    const children = withComputedNodeCounts(node.children ?? []);
    const computedNode = { ...node, children };
    return {
      ...computedNode,
      directEnterpriseCount: countDirectEnterprises(node.id),
      subtreeEnterpriseCount: countSubtreeEnterprises(computedNode),
    };
  });
}

function withComputedChainCounts(chain: IndustryChain): IndustryChain {
  const nodes = flattenNodes(nodeTrees[chain.id] ?? []);
  const nodeIds = new Set(nodes.map((node) => node.id));
  const enterpriseCount = enterprises.filter((enterprise) =>
    enterprise.linkedNodes?.some((linkedNode) =>
      nodeIds.has(linkedNode.nodeId),
    ),
  ).length;

  return {
    ...chain,
    nodeCount: nodes.length,
    enterpriseCount,
  };
}

function formatPercent(value: number, total: number) {
  if (total === 0) return "0%";
  return `${((value / total) * 100).toFixed(1)}%`;
}

function formatNow() {
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(new Date());
}

interface MockDiagnosisNode {
  nodeId: number;
  nodeName: string;
  nodeLevel: number;
  directEnterpriseCount: number;
  subtreeEnterpriseCount: number;
  children?: MockDiagnosisNode[];
}

function mapMockDiagnosisTree(node: MockDiagnosisNode): DiagnosisTreeNode {
  return {
    nodeId: node.nodeId,
    nodeName: node.nodeName,
    nodePath: null,
    nodeLevel: node.nodeLevel,
    keywords: null,
    description: null,
    directEnterpriseCount: node.directEnterpriseCount,
    subtreeEnterpriseCount: node.subtreeEnterpriseCount,
    healthScore: null,
    healthLevel: undefined,
    healthLabel: null,
    weakReasons: [],
    children: (node.children ?? []).map(mapMockDiagnosisTree),
  };
}

function buildChainDistribution() {
  const nodeToChain = getNodeToChainMap();
  const counts = new Map(chains.map((chain) => [chain.id, 0]));
  let unlinkedCount = 0;

  enterprises.forEach((enterprise) => {
    const chainId = enterprise.linkedNodes
      ?.map((linkedNode) => nodeToChain.get(linkedNode.nodeId))
      .find((value): value is number => Boolean(value));

    if (chainId) counts.set(chainId, (counts.get(chainId) ?? 0) + 1);
    else unlinkedCount += 1;
  });

  const records = chains
    .map((chain) => ({
      name: chain.name,
      value: counts.get(chain.id) ?? 0,
    }))
    .filter((item) => item.value > 0);

  if (unlinkedCount > 0) {
    records.push({ name: "未挂链", value: unlinkedCount });
  }

  return records.map((item) => ({
    ...item,
    percent: formatPercent(item.value, enterprises.length),
  }));
}

function buildNodeStatistics() {
  const chainId = chains[0]?.id;
  const rootNodes = chainId
    ? withComputedNodeCounts(nodeTrees[chainId] ?? [])
    : [];
  const colors = ["#2F6BFF", "#00A896", "#F59E0B", "#7C3AED"];

  return rootNodes.map((node, index) => ({
    name: node.name,
    value: node.subtreeEnterpriseCount ?? 0,
    tone: colors[index % colors.length],
  }));
}

function getAdoptionRate() {
  if (matchingTask.results.length === 0) return "0%";
  const accepted = matchingTask.results.filter(
    (result) => result.decision === "ACCEPTED",
  ).length;
  return `${Math.round((accepted / matchingTask.results.length) * 100)}%`;
}

function buildRecentOperations(): DashboardOverview["recentOperations"] {
  return [];
}

function buildEnterpriseRadar(): EnterpriseRadar {
  return {
    metrics: [
      {
        key: "patentCount",
        name: "专利数量",
        value: 60,
        rawValue: "3 条",
        description: "根据企业专利信息条目数计算，5 条及以上记满分",
      },
      {
        key: "bidFrequency",
        name: "招投标频次",
        value: 40,
        rawValue: "2 条",
        description: "根据企业招投标信息条目数计算，5 条及以上记满分",
      },
      {
        key: "productTechRichness",
        name: "产品/技术丰富度",
        value: 80,
        rawValue: "8 个关键词",
        description: "根据主营产品、核心技术、关键词和标签去重后计算",
      },
      {
        key: "businessScopeBreadth",
        name: "经营范围广度",
        value: 75,
        rawValue: "经营范围较完整",
        description: "根据经营范围文本长度和分句数量综合计分",
      },
      {
        key: "chainLinkage",
        name: "产业链关联度",
        value: 70,
        rawValue: "2 个挂接节点",
        description: "根据有效挂接节点数、挂接来源和最高置信度计算",
      },
      {
        key: "businessScale",
        name: "经营规模",
        value: 80,
        rawValue: "产值/税收数据完整",
        description: "根据产值和税收字段是否存在及数值规模计算",
      },
    ],
  };
}

export const mockApi = {
  fetchEnterprises(params: EnterpriseQuery) {
    const keyword = params.keyword?.trim().toLowerCase();
    let records = enterprises.filter(
      (item) => !params.status || item.status === params.status,
    );

    if (keyword) {
      records = records.filter((item) =>
        [
          item.name,
          item.creditCode,
          item.legalPerson,
          item.businessScope,
          item.mainProducts,
        ].some((value) => value?.toLowerCase().includes(keyword)),
      );
    }

    if (params.tagId) {
      records = records.filter((item) =>
        item.tags?.some((tag) => tag.id === params.tagId),
      );
    }

    if (params.nodeId) {
      records = records.filter((item) =>
        item.linkedNodes?.some((node) => node.nodeId === params.nodeId),
      );
    }

    return sleep(paginate(records, params.pageNum, params.pageSize));
  },

  fetchEnterpriseOptions(
    keyword: string,
    limit: number,
  ): Promise<EnterpriseOption[]> {
    const normalizedKeyword = keyword.trim().toLowerCase();
    const records = enterprises
      .filter(
        (item) =>
          item.status === "ACTIVE" &&
          (!normalizedKeyword ||
            item.name.toLowerCase().startsWith(normalizedKeyword)),
      )
      .slice(0, limit)
      .map(({ id, name }) => ({ id, name }));
    return sleep(records);
  },

  fetchEnterprise(id: number) {
    const enterprise = enterprises.find((item) => item.id === id);
    if (!enterprise) return rejectMock("企业不存在");
    return sleep(enterprise);
  },

  fetchEnterpriseProfileAnalysis(
    id: number,
    similarLimit = 5,
  ): Promise<EnterpriseProfileAnalysis> {
    const enterprise = enterprises.find((item) => item.id === id);
    if (!enterprise) return rejectMock("企业不存在");
    const similarEnterprises = enterprises
      .filter((item) => item.id !== id && item.status === "ACTIVE")
      .slice(0, similarLimit)
      .map((item, index) => ({
        enterpriseId: item.id,
        enterpriseName: item.name,
        similarity: Number((0.92 - index * 0.03).toFixed(4)),
        similarityPercent: Math.round((0.92 - index * 0.03) * 100),
        industry: item.industry,
        mainProducts: item.mainProducts,
        reasons: ["所属行业相同：" + (item.industry || "未填写")],
      }));
    return sleep({
      enterpriseId: enterprise.id,
      enterpriseName: enterprise.name,
      radar: buildEnterpriseRadar(),
      similarEnterprises,
      generatedAt: new Date().toISOString(),
    });
  },

  fetchEnterpriseRadar(id: number): Promise<EnterpriseRadar> {
    const enterprise = enterprises.find((item) => item.id === id);
    if (!enterprise) return rejectMock("企业不存在");
    return sleep(buildEnterpriseRadar());
  },

  createEnterprise(data: EnterprisePayload) {
    const enterprise: EnterpriseDetail = {
      id: nextEnterpriseId++,
      status: "UNREGISTERED",
      tags: [],
      linkedNodes: [],
      ...data,
    };
    enterprises.unshift(enterprise);
    return sleep(enterprise);
  },

  updateEnterprise(id: number, data: EnterprisePayload) {
    const index = enterprises.findIndex((item) => item.id === id);
    if (index < 0) return rejectMock("企业不存在");
    enterprises[index] = { ...enterprises[index], ...data };
    return sleep(enterprises[index]);
  },

  deleteEnterprise(id: number) {
    const index = enterprises.findIndex((item) => item.id === id);
    if (index < 0) return rejectMock("企业不存在");
    if (enterprises[index].status !== "UNREGISTERED") {
      return rejectMock("只有未入库企业可以删除");
    }
    enterprises.splice(index, 1);
    return sleep(undefined);
  },

  inboundEnterprise(id: number) {
    const enterprise = enterprises.find((item) => item.id === id);
    if (!enterprise) return rejectMock("企业不存在");
    enterprise.status = "ACTIVE";
    return sleep(undefined);
  },

  outboundEnterprise(id: number) {
    const enterprise = enterprises.find((item) => item.id === id);
    if (!enterprise) return rejectMock("企业不存在");
    enterprise.status = "OUTBOUND";
    return sleep(undefined);
  },

  batchOutboundEnterprises(ids: number[]) {
    enterprises.forEach((enterprise) => {
      if (ids.includes(enterprise.id)) enterprise.status = "OUTBOUND";
    });
    return sleep(undefined);
  },

  batchInboundEnterprises(ids: number[]): Promise<number> {
    let successCount = 0;
    enterprises.forEach((enterprise) => {
      if (
        ids.includes(enterprise.id) &&
        (enterprise.status === "UNREGISTERED" ||
          enterprise.status === "OUTBOUND")
      ) {
        enterprise.status = "ACTIVE";
        successCount++;
      }
    });
    return sleep(successCount);
  },

  importEnterprises(): Promise<ImportResult> {
    return sleep({
      successCount: 0,
      failureCount: 0,
      failures: [],
    });
  },

  fetchEnterpriseImportRecords(params: ImportRecordQuery) {
    const keyword = params.keyword?.trim().toLowerCase();
    let records = importRecords;

    if (params.status) {
      records = records.filter((item) => item.status === params.status);
    }

    if (keyword) {
      records = records.filter((item) =>
        [item.fileName, item.operator].some((value) =>
          value.toLowerCase().includes(keyword),
        ),
      );
    }

    return sleep(paginate(records, params.pageNum, params.pageSize));
  },

  bindEnterpriseTag(id: number, tagId: number) {
    const enterprise = enterprises.find((item) => item.id === id);
    const tag = tags.find((item) => item.id === tagId);
    if (!enterprise) return rejectMock("企业不存在");
    if (!tag) return rejectMock("标签不存在");

    if (!enterprise.tags?.some((item) => item.id === tagId)) {
      enterprise.tags = [...(enterprise.tags ?? []), tag];
      tag.usedCount = (tag.usedCount ?? 0) + 1;
    }

    return sleep(undefined);
  },

  unbindEnterpriseTag(id: number, tagId: number) {
    const enterprise = enterprises.find((item) => item.id === id);
    const tag = tags.find((item) => item.id === tagId);
    if (!enterprise) return rejectMock("企业不存在");

    enterprise.tags = enterprise.tags?.filter((item) => item.id !== tagId);
    if (tag) tag.usedCount = Math.max(0, (tag.usedCount ?? 0) - 1);
    return sleep(undefined);
  },

  fetchTags(params?: { type?: string; keyword?: string }) {
    const keyword = params?.keyword?.trim().toLowerCase();
    let records = tags;

    if (params?.type)
      records = records.filter((tag) => tag.type === params.type);
    if (keyword) {
      records = records.filter((tag) =>
        tag.name.toLowerCase().includes(keyword),
      );
    }

    return sleep(records);
  },

  createTag(data: TagPayload) {
    const tag: Tag = { id: nextTagId++, usedCount: 0, ...data };
    tags.unshift(tag);
    return sleep(tag);
  },

  updateTag(id: number, data: TagPayload) {
    const index = tags.findIndex((tag) => tag.id === id);
    if (index < 0) return rejectMock("标签不存在");
    tags[index] = { ...tags[index], ...data };
    return sleep(tags[index]);
  },

  deleteTag(id: number) {
    const index = tags.findIndex((tag) => tag.id === id);
    if (index < 0) return rejectMock("标签不存在");
    if ((tags[index].usedCount ?? 0) > 0) {
      return rejectMock("标签已被企业使用，需先解绑后删除");
    }
    tags.splice(index, 1);
    return sleep(undefined);
  },

  fetchChains(params?: { keyword?: string }) {
    const keyword = params?.keyword?.trim().toLowerCase();
    const computedChains = chains.map(withComputedChainCounts);
    const records = keyword
      ? computedChains.filter((chain) =>
          chain.name.toLowerCase().includes(keyword),
        )
      : computedChains;
    return sleep(records);
  },

  fetchChainOptions(): Promise<ChainOption[]> {
    return sleep(chains.map(({ id, name }) => ({ id, name })));
  },

  fetchChainNodeCandidates(chainId: number): Promise<ChainNodeCandidate[]> {
    const records = flattenNodes(nodeTrees[chainId] ?? []).map((node) => ({
      id: node.id,
      chainId,
      name: node.name,
      nodePath: node.nodePath ?? `/${node.id}/`,
      keywords: node.keywords ?? null,
      description: node.definition ?? null,
    }));
    return sleep(records);
  },

  createChain(data: ChainPayload) {
    const chain: IndustryChain = {
      id: nextChainId++,
      status: "ENABLED",
      nodeCount: 3,
      enterpriseCount: 0,
      ...data,
    };
    chains.unshift(chain);
    nodeTrees[chain.id] = createDefaultStageNodes(chain.id);
    return sleep(chain);
  },

  updateChain(id: number, data: ChainPayload) {
    const index = chains.findIndex((chain) => chain.id === id);
    if (index < 0) return rejectMock("产业链不存在");
    chains[index] = { ...chains[index], ...data };
    return sleep(chains[index]);
  },

  deleteChain(id: number) {
    const index = chains.findIndex((chain) => chain.id === id);
    if (index < 0) return rejectMock("产业链不存在");
    chains.splice(index, 1);
    delete nodeTrees[id];
    return sleep(undefined);
  },

  fetchNodeTree(chainId: number) {
    return sleep(withComputedNodeCounts(nodeTrees[chainId] ?? []));
  },

  createNode(chainId: number, data: ChainNodePayload) {
    const chain = chains.find((item) => item.id === chainId);
    if (!chain) return rejectMock("产业链不存在");

    const allNodes = flattenNodes(nodeTrees[chainId] ?? []);
    const parent = allNodes.find((node) => node.id === data.parentId);
    const node: ChainNode = {
      id: nextNodeId++,
      chainId,
      levelNo: parent ? (parent.levelNo ?? 1) + 1 : 1,
      status: "ENABLED",
      directEnterpriseCount: 0,
      subtreeEnterpriseCount: 0,
      children: [],
      ...data,
    };

    if (parent) parent.children = [...(parent.children ?? []), node];
    else nodeTrees[chainId] = [...(nodeTrees[chainId] ?? []), node];
    return sleep(node);
  },

  updateNode(id: number, data: UpdateChainNodePayload) {
    try {
      const node = data.parentChangeRequested
        ? moveNodeInTree(nodeTrees, id, data.parentId ?? null, data.sortNo)
        : findNodeLocation(nodeTrees, id)?.node;
      if (!node) return rejectMock("节点不存在");

      node.name = data.name;
      node.keywords = data.keywords;
      node.definition = data.definition;
      if (data.sortNo !== undefined) node.sortNo = data.sortNo;
      return sleep(node);
    } catch (error) {
      return rejectMock(
        error instanceof Error ? error.message : "节点更新失败",
      );
    }
  },

  moveNode(id: number, data: MoveNodeRequest) {
    try {
      return sleep(
        moveNodeInTree(nodeTrees, id, data.newParentId, data.sortOrder),
      );
    } catch (error) {
      return rejectMock(
        error instanceof Error ? error.message : "节点移动失败",
      );
    }
  },

  sortNodes(data: SortNodesRequest) {
    const firstOrder = data.nodeOrders[0];
    if (!firstOrder) return rejectMock("排序节点不能为空");
    const firstLocation = findNodeLocation(nodeTrees, firstOrder.nodeId);
    if (!firstLocation) return rejectMock("节点不存在");

    const siblings = getSiblingGroup(
      nodeTrees,
      firstLocation.chainId,
      data.parentId,
    );
    if (!siblings) return rejectMock("父节点不存在");
    const siblingIds = new Set(siblings.map((node) => node.id));
    if (data.nodeOrders.some((order) => !siblingIds.has(order.nodeId))) {
      return rejectMock("排序节点不属于指定父节点");
    }

    const orderByNodeId = new Map(
      data.nodeOrders.map((order) => [order.nodeId, order.sortOrder]),
    );
    siblings.sort(
      (left, right) =>
        (orderByNodeId.get(left.id) ?? left.sortNo ?? 0) -
        (orderByNodeId.get(right.id) ?? right.sortNo ?? 0),
    );
    normalizeSiblingOrder(siblings);
    return sleep(undefined);
  },

  deleteNode(id: number) {
    const node = Object.values(nodeTrees)
      .flatMap(flattenNodes)
      .find((item) => item.id === id);
    if (!node) return rejectMock("节点不存在");
    if (node.stageType) {
      return rejectMock("上下游阶段节点不允许删除");
    }
    if ((node.children ?? []).length > 0) {
      return rejectMock("产业链节点存在子节点，不能删除");
    }
    if (countDirectEnterprises(id) > 0) {
      return rejectMock("产业链节点已关联企业，不能删除");
    }

    Object.keys(nodeTrees).forEach((chainId) => {
      nodeTrees[Number(chainId)] = removeNodeById(
        nodeTrees[Number(chainId)],
        id,
      );
    });
    return sleep(undefined);
  },

  linkEnterpriseToNode(nodeId: number, enterpriseId: number) {
    const enterprise = enterprises.find((item) => item.id === enterpriseId);
    const node = Object.values(nodeTrees)
      .flatMap(flattenNodes)
      .find((item) => item.id === nodeId);
    if (!enterprise) return rejectMock("企业不存在");
    if (!node) return rejectMock("节点不存在");
    if (
      enterprise.linkedNodes?.some((linkedNode) => linkedNode.nodeId === nodeId)
    ) {
      return rejectMock("企业已挂接该节点");
    }

    enterprise.linkedNodes = [
      ...(enterprise.linkedNodes ?? []),
      {
        nodeId,
        nodeName: node.name,
        nodePath: node.name,
        source: "MANUAL",
        createdAt: formatNow(),
      },
    ];

    return sleep(undefined);
  },

  batchLinkEnterprisesToNode(
    nodeId: number,
    enterpriseIds: number[],
  ): Promise<BatchLinkEnterpriseResult> {
    const node = Object.values(nodeTrees)
      .flatMap(flattenNodes)
      .find((item) => item.id === nodeId);
    const seenEnterpriseIds = new Set<number>();
    const items: BatchLinkEnterpriseItem[] = enterpriseIds.map(
      (enterpriseId) => {
        if (seenEnterpriseIds.has(enterpriseId)) {
          return {
            enterpriseId,
            status: "DUPLICATE_IN_REQUEST",
            message: "请求内重复",
          };
        }
        seenEnterpriseIds.add(enterpriseId);

        const enterprise = enterprises.find((item) => item.id === enterpriseId);
        if (!node) {
          return {
            enterpriseId,
            status: "FAILED",
            message: "节点不存在",
          };
        }
        if (!enterprise) {
          return {
            enterpriseId,
            status: "FAILED",
            message: "企业不存在",
          };
        }
        if (
          enterprise.linkedNodes?.some(
            (linkedNode) => linkedNode.nodeId === nodeId,
          )
        ) {
          return {
            enterpriseId,
            status: "ALREADY_LINKED",
            message: "已挂接",
          };
        }

        enterprise.linkedNodes = [
          ...(enterprise.linkedNodes ?? []),
          {
            nodeId,
            nodeName: node.name,
            nodePath: node.name,
            source: "MANUAL",
            createdAt: formatNow(),
          },
        ];

        return {
          enterpriseId,
          status: "LINKED",
          message: "挂接成功",
        };
      },
    );
    const linkedCount = items.filter((item) => item.status === "LINKED").length;
    const failedCount = items.filter((item) => item.status === "FAILED").length;

    return sleep({
      total: items.length,
      linkedCount,
      skippedCount: items.length - linkedCount - failedCount,
      failedCount,
      items,
    });
  },

  unlinkEnterpriseFromNode(nodeId: number, enterpriseId: number) {
    const enterprise = enterprises.find((item) => item.id === enterpriseId);
    if (!enterprise) return rejectMock("企业不存在");
    enterprise.linkedNodes = enterprise.linkedNodes?.filter(
      (node) => node.nodeId !== nodeId,
    );
    return sleep(undefined);
  },

  fetchNodeEnterprises(nodeId: number): Promise<NodeEnterpriseResult> {
    const records: NodeEnterprise[] = enterprises
      .filter((enterprise) =>
        enterprise.linkedNodes?.some((node) => node.nodeId === nodeId),
      )
      .map((enterprise) => ({
        enterpriseId: enterprise.id,
        enterpriseName: enterprise.name,
        source:
          enterprise.linkedNodes?.find((node) => node.nodeId === nodeId)
            ?.source ?? "MANUAL",
        createdAt: enterprise.linkedNodes?.find(
          (node) => node.nodeId === nodeId,
        )?.createdAt,
      }));

    return sleep({
      nodeId,
      directEnterpriseCount: records.length,
      subtreeEnterpriseCount: records.length,
      records,
    });
  },

  createMatchingTask(data: { enterpriseId: number; chainId: number }) {
    matchingTask = {
      taskId: nextTaskId++,
      enterpriseId: data.enterpriseId,
      chainId: data.chainId,
      status: "PROCESSING",
      results: [],
    };
    matchingTaskPollCount = 0;
    return sleep(matchingTask);
  },

  createBatchMatchingTasks(data: {
    allEnterprises: boolean;
    enterpriseIds?: number[];
    allChains: boolean;
    chainIds?: number[];
  }) {
    const enterpriseIds = data.allEnterprises
      ? enterprises
          .filter((enterprise) => enterprise.status === "ACTIVE")
          .map((enterprise) => enterprise.id)
      : (data.enterpriseIds ?? []);
    const chainIds = data.allChains
      ? chains.map((chain) => chain.id)
      : (data.chainIds ?? []);
    const tasks = enterpriseIds.flatMap((enterpriseId) =>
      chainIds.map((chainId) => ({
        taskId: nextTaskId++,
        enterpriseId,
        chainId,
        status: "PROCESSING",
        results: [],
      })),
    );
    if (tasks.length > 0) {
      matchingTask = tasks[0];
      matchingTaskPollCount = 0;
    }
    return sleep({
      enterpriseCount: enterpriseIds.length,
      chainCount: chainIds.length,
      totalPairs: tasks.length,
      submittedCount: tasks.length,
      tasks,
    });
  },

  fetchMatchingTask(taskId: number) {
    if (matchingTask.taskId !== taskId) return rejectMock("推荐任务不存在");
    if (
      (matchingTask.status === "PROCESSING" ||
        matchingTask.status === "RETRYING") &&
      matchingTaskPollCount > 0
    ) {
      matchingTask.status = "COMPLETED";
    }
    matchingTaskPollCount += 1;
    return sleep(matchingTask);
  },

  cancelMatchingTask(taskId: number) {
    if (matchingTask.taskId !== taskId) return rejectMock("推荐任务不存在");
    if (matchingTask.status !== "PROCESSING") {
      return rejectMock("仅处理中任务支持取消");
    }
    matchingTask.status = "CANCELLED";
    return sleep(matchingTask);
  },

  revokeMatchingTask(taskId: number) {
    if (matchingTask.taskId !== taskId) return rejectMock("推荐任务不存在");
    matchingTask.results.forEach((result) => {
      if (result.decision === "ACCEPTED" || result.decision === "MANUAL") {
        const enterprise = enterprises.find(
          (item) => item.id === matchingTask.enterpriseId,
        );
        if (enterprise) {
          enterprise.linkedNodes = enterprise.linkedNodes?.filter(
            (node) => node.nodeId !== result.nodeId,
          );
        }
      }
    });
    matchingTask.status = "CANCELLED";
    matchingTask.results = [];
    return sleep(matchingTask);
  },

  retryMatchingTask(taskId: number) {
    if (matchingTask.taskId !== taskId) return rejectMock("推荐任务不存在");
    if (
      matchingTask.status !== "FAILED" &&
      matchingTask.status !== "TIMEOUT" &&
      matchingTask.status !== "CANCELLED"
    ) {
      return rejectMock("当前任务状态不支持重试");
    }

    matchingTask = {
      taskId: nextTaskId++,
      enterpriseId: matchingTask.enterpriseId,
      chainId: matchingTask.chainId,
      status: "PROCESSING",
      results: [],
    };
    matchingTaskPollCount = 0;
    return sleep(matchingTask);
  },

  acceptMatchingResult(resultId: number) {
    const result = matchingTask.results.find(
      (item) => item.resultId === resultId,
    );
    if (!result) return rejectMock("推荐结果不存在");
    result.decision = "ACCEPTED";
    return sleep({ resultId, decision: "ACCEPTED" });
  },

  rejectMatchingResult(resultId: number, rejectReason: string) {
    const result = matchingTask.results.find(
      (item) => item.resultId === resultId,
    );
    if (!result) return rejectMock("推荐结果不存在");
    result.decision = "REJECTED";
    result.rejectReason = rejectReason;
    return sleep({ resultId, decision: "REJECTED", rejectReason });
  },

  manualConfirmMatching(data: { enterpriseId: number; nodeId: number }) {
    return this.linkEnterpriseToNode(data.nodeId, data.enterpriseId).then(
      () => ({
        enterpriseId: data.enterpriseId,
        nodeId: data.nodeId,
        source: "MANUAL",
        linked: true,
      }),
    );
  },

  fetchBatchTasks(taskIds: number[]) {
    const tasks = taskIds.map((id) => {
      if (matchingTask.taskId === id) {
        if (
          (matchingTask.status === "PROCESSING" ||
            matchingTask.status === "RETRYING") &&
          matchingTaskPollCount > 0
        ) {
          matchingTask.status = "COMPLETED";
        }
        matchingTaskPollCount += 1;
        return { ...matchingTask };
      }
      return {
        taskId: id,
        enterpriseId: 1,
        chainId: 1,
        status: "COMPLETED" as const,
        results: [],
      };
    });

    const completedCount = tasks.filter((t) => t.status === "COMPLETED").length;
    const processingCount = tasks.filter(
      (t) => t.status === "PROCESSING" || t.status === "RETRYING",
    ).length;
    const failedCount = tasks.filter((t) => t.status === "FAILED").length;

    return sleep({
      total: tasks.length,
      processingCount,
      completedCount,
      failedCount,
      acceptedCount: 0,
      rejectedCount: 0,
      tasks,
    });
  },

  searchNodesByKeyword(
    keyword: string,
    chainId?: number,
    pageNum = 1,
    pageSize = 20,
  ): Promise<PageResult<ChainNode>> {
    const lowerKeyword = keyword.toLowerCase();
    let allNodes: ChainNode[] = [];

    if (chainId !== undefined) {
      allNodes = flattenNodes(nodeTrees[chainId] ?? []);
    } else {
      allNodes = Object.values(nodeTrees).flatMap(flattenNodes);
    }

    const matched = allNodes.filter((node) =>
      (node.keywords ?? "").toLowerCase().includes(lowerKeyword),
    );

    return sleep(paginate(matched, pageNum, pageSize));
  },

  fetchDashboard(): Promise<DashboardOverview> {
    const activeEnterpriseCount = enterprises.filter(
      (enterprise) => enterprise.status === "ACTIVE",
    ).length;
    const awaitingMatchingCount = enterprises.filter(
      (enterprise) =>
        enterprise.status === "ACTIVE" && !enterprise.linkedNodes?.length,
    ).length;
    const acceptedCount = matchingTask.results.filter(
      (result) => result.decision === "ACCEPTED",
    ).length;
    const rejectedCount = matchingTask.results.filter(
      (result) => result.decision === "REJECTED",
    ).length;
    const pendingCount = matchingTask.results.filter(
      (result) => (result.decision ?? "PENDING") === "PENDING",
    ).length;
    const manualLinkCount = enterprises.filter((enterprise) =>
      enterprise.linkedNodes?.some((node) => node.source === "MANUAL"),
    ).length;
    const averageConfidence =
      matchingTask.results.length === 0
        ? 0
        : Number(
            (
              matchingTask.results.reduce(
                (sum, result) => sum + result.confidence,
                0,
              ) / matchingTask.results.length
            ).toFixed(2),
          );

    return sleep({
      metrics: [
        {
          label: "企业总数",
          value: enterprises.length,
          unit: "家",
          tone: "blue",
        },
        {
          label: "已入库企业",
          value: activeEnterpriseCount,
          unit: "家",
          tone: "green",
        },
        {
          label: "待智能挂链",
          value: awaitingMatchingCount,
          unit: "家",
          tone: "amber",
        },
        {
          label: "推荐采纳率",
          value: getAdoptionRate(),
          tone: "purple",
        },
      ],
      chainDistribution: buildChainDistribution(),
      nodeStatistics: buildNodeStatistics(),
      matchingSummary: {
        pending: pendingCount,
        accepted: acceptedCount,
        rejected: rejectedCount,
        manual: manualLinkCount,
        averageConfidence,
        weeklyRecommendations: matchingTask.results.length,
      },
      weeklyProgress: [],
      recentOperations: buildRecentOperations(),
    });
  },

  fetchDiagnosisTree(chainId: number): Promise<DiagnosisTreeNode> {
    return sleep(
      mapMockDiagnosisTree({
        nodeId: chainId,
        nodeName: "Mock 产业链",
        nodeLevel: 0,
        directEnterpriseCount: 0,
        subtreeEnterpriseCount: 0,
        children: [],
      }),
    );
  },

  createAnalysisReport(
    data: CreateAnalysisReportRequest,
  ): Promise<AnalysisReportTask> {
    const id = nextReportId++;
    const task: AnalysisReportTask = {
      id,
      chainId: data.chainId,
      chainName: chains.find((item) => item.id === data.chainId)?.name,
      reportType: data.reportType,
      scopeType: data.scopeType,
      selectedNodeIds: data.nodeIds,
      resolvedNodeIds: data.nodeIds,
      status: "PROCESSING",
      retryCount: 0,
      createdBy: "admin",
      createdAt: formatNow(),
      completedAt: null,
    };
    mockReports.unshift(task);
    mockReportPollCount[id] = 0;
    return sleep(task);
  },

  fetchAnalysisReports(
    params: AnalysisReportQuery,
  ): Promise<PageResult<AnalysisReportTask>> {
    let records = [...mockReports];
    if (params.chainId !== undefined) {
      records = records.filter((item) => item.chainId === params.chainId);
    }
    if (params.status) {
      records = records.filter((item) => item.status === params.status);
    }
    return sleep(paginate(records, params.pageNum, params.pageSize));
  },

  fetchAnalysisReportDetail(reportId: number): Promise<AnalysisReportDetail> {
    const task = mockReports.find((item) => item.id === reportId);
    if (!task) return rejectMock("报告任务不存在");
    let status = task.status;
    if (status === "PROCESSING") {
      const count = (mockReportPollCount[reportId] ?? 0) + 1;
      mockReportPollCount[reportId] = count;
      // 第二次轮询后置为完成，模拟异步任务推进
      if (count >= 2) {
        status = "COMPLETED";
        task.status = "COMPLETED";
        task.completedAt = formatNow();
      }
    }
    const detail: AnalysisReportDetail = {
      ...task,
      status,
      title: `${task.chainName ?? "产业链"}健康分析报告`,
      contentMarkdown:
        status === "COMPLETED"
          ? "# 健康分析报告\n\n产业链整体健康度为 82 分。"
          : null,
      contentHtml:
        status === "COMPLETED"
          ? "<h1>健康分析报告</h1><p>产业链整体健康度为 <strong>82</strong> 分。</p>"
          : null,
      pdfFilePath: status === "COMPLETED" ? "/mock/report.pdf" : null,
      metricsSnapshot: status === "COMPLETED" ? '{"overall":82}' : null,
    };
    return sleep(detail);
  },

  retryAnalysisReport(reportId: number): Promise<AnalysisReportTask> {
    const task = mockReports.find((item) => item.id === reportId);
    if (!task) return rejectMock("报告任务不存在");
    if (task.status !== "FAILED" && task.status !== "CANCELLED") {
      return rejectMock("仅 FAILED、CANCELLED 状态的任务可重试");
    }
    const id = nextReportId++;
    const retry: AnalysisReportTask = {
      ...task,
      id,
      status: "PROCESSING",
      retryFromTaskId: reportId,
      retryCount: (task.retryCount ?? 0) + 1,
      errorMessage: null,
      createdAt: formatNow(),
      updatedAt: formatNow(),
      completedAt: null,
    };
    mockReports.unshift(retry);
    mockReportPollCount[id] = 0;
    return sleep(retry);
  },

  downloadAnalysisReport(
    reportId: number,
    format: AnalysisReportDownloadFormat,
  ): Promise<Blob> {
    const task = mockReports.find((item) => item.id === reportId);
    if (!task) return rejectMock("报告任务不存在");
    if (task.status !== "COMPLETED") {
      return rejectMock("仅 COMPLETED 状态的报告可下载");
    }
    const content =
      format === "pdf"
        ? new Uint8Array([0x25, 0x50, 0x44, 0x46])
        : "# 健康分析报告\n\n产业链整体健康度为 82 分。";
    return sleep(new Blob([content]));
  },
};
