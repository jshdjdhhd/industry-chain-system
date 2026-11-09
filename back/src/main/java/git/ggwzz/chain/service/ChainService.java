package git.ggwzz.chain.service;

import git.ggwzz.chain.dto.CreateChainRequest;
import git.ggwzz.chain.dto.CreateNodeRequest;
import git.ggwzz.chain.dto.MoveNodeRequest;
import git.ggwzz.chain.dto.SortNodesRequest;
import git.ggwzz.chain.dto.UpdateChainRequest;
import git.ggwzz.chain.dto.UpdateNodeRequest;
import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import git.ggwzz.chain.vo.ChainOptionVO;
import git.ggwzz.chain.vo.ChainVO;
import git.ggwzz.chain.vo.EnterpriseCountVO;
import git.ggwzz.chain.vo.NodeVO;
import git.ggwzz.chain.vo.TreeNodeVO;
import git.ggwzz.common.api.PageResult;

import java.util.List;

/**
 * 产业链管理服务。
 *
 * <p>提供产业链 CRUD、节点树 CRUD、企业数量统计能力。
 * 挂链/解绑由 {@code EnterpriseNodeRelService} 提供，不在本服务范围。</p>
 */
public interface ChainService {

    /** 创建产业链 */
    ChainVO createChain(CreateChainRequest request);

    /** 产业链分页列表 */
    PageResult<ChainVO> listChains(Integer pageNum, Integer pageSize);

    /** 查询产业链下拉选项（仅 ID 与名称） */
    List<ChainOptionVO> listChainOptions();

    /** 产业链详情 */
    ChainVO getChain(Long chainId);

    /** 更新产业链（部分更新） */
    ChainVO updateChain(Long chainId, UpdateChainRequest request);

    /** 删除产业链（级联逻辑删节点与挂链关系） */
    void deleteChain(Long chainId);

    /** 创建节点（自动维护 parent_id / node_path / level） */
    NodeVO createNode(CreateNodeRequest request);

    /** 获取产业链树（含每节点企业数） */
    TreeNodeVO getChainTree(Long chainId);

    /** 查询产业链节点候选，不包含企业数统计 */
    List<ChainNodeCandidateVO> listNodeCandidates(Long chainId);

    /** 节点分页列表，可按 parentId 过滤子级 */
    PageResult<NodeVO> listNodes(Long chainId, Long parentId, Integer pageNum, Integer pageSize);

    /** 更新节点（部分更新） */
    NodeVO updateNode(Long nodeId, UpdateNodeRequest request);

    /** 移动节点并级联重写子树路径 */
    NodeVO moveNode(Long nodeId, MoveNodeRequest request);

    /** 批量更新同级节点排序 */
    void sortNodes(SortNodesRequest request);

    /** 删除节点（叶子节点可删，有子节点抛业务异常） */
    void deleteNode(Long nodeId);

    /** 节点名称模糊搜索（右模糊） */
    PageResult<NodeVO> searchNodes(String keyword, Integer pageNum, Integer pageSize);

    /**
     * 节点关键词搜索（按知识库 keywords 字段全模糊匹配）。
     *
     * <p>与 {@link #searchNodes} 区别：前者按节点名称右模糊，本方法按关键词全模糊，
     * 用于"节点关键词词库"检索。{@code chainId} 为 null 时跨产业链搜索。</p>
     *
     * @param keyword  搜索关键词，不能为空白
     * @param chainId  产业链 ID，null 表示不限定产业链
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页大小
     * @return 命中节点分页
     */
    PageResult<NodeVO> searchByKeywords(String keyword, Long chainId, int pageNum, int pageSize);

    /** 节点企业数量统计（直接 + 含下级总数） */
    EnterpriseCountVO getEnterpriseCount(Long nodeId);
}
