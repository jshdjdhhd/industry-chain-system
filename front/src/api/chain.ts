import { get, post, put, remove } from "./http";
import { isMockApiEnabled } from "./mode";
import { mockApi } from "./mock";
import type {
  BatchLinkEnterpriseResult,
  ChainNode,
  ChainNodeCandidate,
  ChainNodePayload,
  ChainOption,
  ChainStageType,
  ChainPayload,
  IndustryChain,
  MoveNodeRequest,
  NodeEnterpriseResult,
  PageResult,
  SortNodesRequest,
  UpdateChainNodePayload,
} from "@/types/domain";

interface BackendChainVO {
  id: number;
  chainName: string;
  description?: string | null;
  nodeCount: number;
  enterpriseCount: number;
  createTime?: string;
}

interface BackendTreeNodeVO {
  id: number;
  nodeName: string;
  stageType?: ChainStageType | null;
  nodeLevel?: number;
  parentId?: number | null;
  sortOrder?: number;
  keywords?: string | null;
  description?: string | null;
  directEnterpriseCount?: number;
  subtreeEnterpriseCount?: number;
  children?: BackendTreeNodeVO[];
}

interface BackendNodeVO {
  id: number;
  chainId?: number;
  parentId?: number | null;
  nodeName: string;
  stageType?: ChainStageType | null;
  nodeLevel?: number;
  keywords?: string | null;
  description?: string | null;
  nodePath?: string;
  sortOrder?: number;
  createTime?: string;
}

interface BackendNodeEnterpriseVO {
  enterpriseId: number;
  enterpriseName: string;
  source: string;
  createTime: string;
}

interface BackendChainPayload {
  chainName: string;
  description?: string;
}

interface BackendNodePayload {
  parentId?: number | null;
  nodeName: string;
  keywords?: string;
  description?: string;
  sortOrder?: number;
  parentChangeRequested?: boolean;
}

function mapBackendChain(chain: BackendChainVO): IndustryChain {
  return {
    id: chain.id,
    name: chain.chainName,
    description: chain.description,
    nodeCount: chain.nodeCount,
    enterpriseCount: chain.enterpriseCount,
  };
}

export function mapBackendChainPage(
  page: PageResult<BackendChainVO>,
): IndustryChain[] {
  return page.records.map(mapBackendChain);
}

export function toBackendChainPayload(data: ChainPayload): BackendChainPayload {
  return {
    chainName: data.name,
    description: data.description,
  };
}

function mapBackendNode(node: BackendNodeVO): ChainNode {
  return {
    id: node.id,
    chainId: node.chainId,
    parentId: node.parentId,
    name: node.nodeName,
    stageType: node.stageType ?? null,
    levelNo: node.nodeLevel,
    keywords: node.keywords,
    definition: node.description,
    nodePath: node.nodePath,
    sortNo: node.sortOrder,
    children: [],
  };
}

function mapBackendTreeNode(node: BackendTreeNodeVO): ChainNode {
  return {
    id: node.id,
    name: node.nodeName,
    stageType: node.stageType ?? null,
    levelNo: node.nodeLevel,
    parentId: node.parentId ?? null,
    sortNo: node.sortOrder,
    keywords: node.keywords,
    definition: node.description,
    directEnterpriseCount: node.directEnterpriseCount ?? 0,
    subtreeEnterpriseCount: node.subtreeEnterpriseCount ?? 0,
    children: (node.children ?? []).map(mapBackendTreeNode),
  };
}

export function mapBackendNodeTree(root: BackendTreeNodeVO): ChainNode[] {
  return (root.children ?? []).map(mapBackendTreeNode);
}

export function toBackendNodePayload(
  data: ChainNodePayload,
): BackendNodePayload {
  return {
    parentId: data.parentId,
    nodeName: data.name,
    keywords: data.keywords,
    description: data.definition,
    sortOrder: data.sortNo,
  };
}

function toBackendUpdateNodePayload(
  data: UpdateChainNodePayload,
): BackendNodePayload {
  return {
    ...toBackendNodePayload(data),
    parentChangeRequested: data.parentChangeRequested,
  };
}

export function mapBackendNodeEnterpriseList(
  nodeId: number,
  relations: BackendNodeEnterpriseVO[],
): NodeEnterpriseResult {
  return {
    nodeId,
    directEnterpriseCount: relations.length,
    subtreeEnterpriseCount: relations.length,
    records: relations.map((relation) => ({
      enterpriseId: relation.enterpriseId,
      enterpriseName: relation.enterpriseName,
      source: relation.source,
      createdAt: relation.createTime,
    })),
  };
}

export function fetchChains(params?: {
  keyword?: string;
}): Promise<IndustryChain[]> {
  if (isMockApiEnabled()) return mockApi.fetchChains(params);
  return get<PageResult<BackendChainVO>>("/chains", {
    params: { pageNum: 1, pageSize: 1000 },
  }).then((page) => {
    const chains = mapBackendChainPage(page);
    const keyword = params?.keyword?.trim().toLowerCase();
    if (!keyword) return chains;
    return chains.filter((chain) => chain.name.toLowerCase().includes(keyword));
  });
}

export function fetchChainOptions(): Promise<ChainOption[]> {
  if (isMockApiEnabled()) return mockApi.fetchChainOptions();
  return get<ChainOption[]>("/chains/options");
}

export function fetchChainNodeCandidates(
  chainId: number,
): Promise<ChainNodeCandidate[]> {
  if (isMockApiEnabled()) return mockApi.fetchChainNodeCandidates(chainId);
  return get<ChainNodeCandidate[]>(`/chains/${chainId}/node-options`);
}

export function createChain(data: ChainPayload): Promise<IndustryChain> {
  if (isMockApiEnabled()) return mockApi.createChain(data);
  return post<BackendChainVO>("/chains", toBackendChainPayload(data)).then(
    mapBackendChain,
  );
}

export function updateChain(
  id: number,
  data: ChainPayload,
): Promise<IndustryChain> {
  if (isMockApiEnabled()) return mockApi.updateChain(id, data);
  return put<BackendChainVO>(`/chains/${id}`, toBackendChainPayload(data)).then(
    mapBackendChain,
  );
}

export function deleteChain(id: number): Promise<void> {
  if (isMockApiEnabled()) return mockApi.deleteChain(id);
  return remove(`/chains/${id}`);
}

export function fetchNodeTree(chainId: number): Promise<ChainNode[]> {
  if (isMockApiEnabled()) return mockApi.fetchNodeTree(chainId);
  return get<BackendTreeNodeVO>(`/chains/${chainId}/tree`).then(
    mapBackendNodeTree,
  );
}

export function createNode(
  chainId: number,
  data: ChainNodePayload,
): Promise<ChainNode> {
  if (isMockApiEnabled()) return mockApi.createNode(chainId, data);
  return post<BackendNodeVO>(
    `/chains/${chainId}/nodes`,
    toBackendNodePayload(data),
  ).then(mapBackendNode);
}

export function updateNode(
  id: number,
  data: UpdateChainNodePayload,
): Promise<ChainNode> {
  if (isMockApiEnabled()) return mockApi.updateNode(id, data);
  return put<BackendNodeVO>(
    `/chains/nodes/${id}`,
    toBackendUpdateNodePayload(data),
  ).then(mapBackendNode);
}

export function moveNode(
  id: number,
  data: MoveNodeRequest,
): Promise<ChainNode> {
  if (isMockApiEnabled()) return mockApi.moveNode(id, data);
  return put<BackendNodeVO>(`/chains/nodes/${id}/move`, data).then(
    mapBackendNode,
  );
}

export function sortNodes(data: SortNodesRequest): Promise<void> {
  if (isMockApiEnabled()) return mockApi.sortNodes(data);
  return put<void>("/chains/nodes/sort", data);
}

export function deleteNode(id: number): Promise<void> {
  if (isMockApiEnabled()) return mockApi.deleteNode(id);
  return remove(`/chains/nodes/${id}`);
}

export function linkEnterpriseToNode(
  nodeId: number,
  enterpriseId: number,
): Promise<void> {
  if (isMockApiEnabled())
    return mockApi.linkEnterpriseToNode(nodeId, enterpriseId);
  return post(`/nodes/${nodeId}/enterprises`, { enterpriseId });
}

export function batchLinkEnterprisesToNode(
  nodeId: number,
  enterpriseIds: number[],
): Promise<BatchLinkEnterpriseResult> {
  if (isMockApiEnabled())
    return mockApi.batchLinkEnterprisesToNode(nodeId, enterpriseIds);
  return post<BatchLinkEnterpriseResult>(`/nodes/${nodeId}/enterprises/batch`, {
    enterpriseIds,
  });
}

export function unlinkEnterpriseFromNode(
  nodeId: number,
  enterpriseId: number,
): Promise<void> {
  if (isMockApiEnabled())
    return mockApi.unlinkEnterpriseFromNode(nodeId, enterpriseId);
  return remove(`/nodes/${nodeId}/enterprises/${enterpriseId}`);
}

export function fetchNodeEnterprises(
  nodeId: number,
): Promise<NodeEnterpriseResult> {
  if (isMockApiEnabled()) return mockApi.fetchNodeEnterprises(nodeId);
  return get<BackendNodeEnterpriseVO[]>(`/nodes/${nodeId}/enterprises`).then(
    (relations) => mapBackendNodeEnterpriseList(nodeId, relations),
  );
}

export function mapBackendNodePage(
  page: PageResult<BackendNodeVO>,
): PageResult<ChainNode> {
  return {
    records: page.records.map(mapBackendNode),
    total: page.total,
    pageNum: page.pageNum,
    pageSize: page.pageSize,
  };
}

export function searchNodesByKeyword(params: {
  keyword: string;
  chainId?: number;
  pageNum?: number;
  pageSize?: number;
}): Promise<PageResult<ChainNode>> {
  if (isMockApiEnabled()) {
    return mockApi.searchNodesByKeyword(
      params.keyword,
      params.chainId,
      params.pageNum ?? 1,
      params.pageSize ?? 20,
    );
  }
  return get<PageResult<BackendNodeVO>>("/chains/nodes/search-by-keyword", {
    params: {
      keyword: params.keyword,
      chainId: params.chainId,
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? 20,
    },
  }).then(mapBackendNodePage);
}
