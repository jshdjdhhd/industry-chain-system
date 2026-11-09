package git.ggwzz.analysis.mapper;

import git.ggwzz.analysis.mapper.projection.EnterpriseProfileDetailRow;
import git.ggwzz.analysis.mapper.projection.EnterpriseProfileRow;
import git.ggwzz.analysis.mapper.projection.MatchingEvidenceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 产业链诊断分析查询 Mapper。
 *
 * <p>只读聚合查询，服务于诊断树健康度计算。复用 chain 模块节点与挂链关系表，
 * 不新增业务表。</p>
 */
@Mapper
public interface AnalysisQueryMapper {

    /**
     * 查询某产业链下全部有效挂链企业的画像完整性及挂载节点路径（7.1 健康度口径）。
     *
     * <p>只返回布尔画像完整性字段，不携带企业画像明细，避免 GROUP_CONCAT 子查询开销。
     * 结果按挂链关系返回，未对企业去重；调用方在内存按 node_path 前缀归并时去重。
     * 一次查询取全链数据，避免按节点 N+1。</p>
     *
     * @param chainId 产业链 ID
     * @return 链下全部有效挂链企业的画像完整性投影行
     */
    List<EnterpriseProfileRow> selectEnterpriseProfilesByChainId(
            @Param("chainId") Long chainId);

    /**
     * 查询某产业链下全部有效挂链企业的画像明细（7.4 报告上下文口径）。
     *
     * <p>返回企业画像明细字段（名称/状态/地区/行业/主营产品/核心技术/产值/税收/标签名），
     * 供健康分析报告上下文组装企业样本使用。与 {@link #selectEnterpriseProfilesByChainId}
     * 分离，7.1 健康度路径零性能回退。</p>
     *
     * @param chainId 产业链 ID
     * @return 链下全部有效挂链企业的画像明细投影行
     */
    List<EnterpriseProfileDetailRow> selectEnterpriseProfilesByChainIdForReport(
            @Param("chainId") Long chainId);

    /**
     * 查询某产业链下范围内已采纳/待决策的历史推荐证据。
     *
     * <p>取 matching_result（decision IN ACCEPTED, PENDING）关联 matching_task（chain_id 过滤）
     * 与 enterprise（取企业名称），按 nodeId 在内存中过滤到 resolved 范围。
     * 至多 3 表 JOIN，符合单条 SQL 不超过 3 表 JOIN 约束。</p>
     *
     * @param chainId 产业链 ID
     * @param limit   最大返回行数
     * @return 历史推荐证据投影行
     */
    List<MatchingEvidenceRow> selectMatchingEvidenceByChainId(
            @Param("chainId") Long chainId,
            @Param("limit") int limit);
}
