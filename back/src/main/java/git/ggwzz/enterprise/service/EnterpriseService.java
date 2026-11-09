package git.ggwzz.enterprise.service;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.enterprise.dto.BindTagRequest;
import git.ggwzz.enterprise.dto.CreateEnterpriseRequest;
import git.ggwzz.enterprise.dto.EnterpriseQueryRequest;
import git.ggwzz.enterprise.dto.UpdateEnterpriseRequest;
import git.ggwzz.enterprise.vo.EnterpriseDetailVO;
import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import git.ggwzz.enterprise.vo.EnterpriseVO;

import java.util.List;

/**
 * 企业管理服务。
 */
public interface EnterpriseService {

    /**
     * 企业分页列表（支持关键词/状态/标签/节点筛选）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<EnterpriseVO> list(EnterpriseQueryRequest query);

    /**
     * 查询 ACTIVE 企业选项（仅 ID 与名称）。
     *
     * @param keyword 关键词，仅匹配企业名称前缀
     * @param limit   最大返回条数
     * @return 企业选项列表
     */
    List<EnterpriseOptionVO> listOptions(String keyword, int limit);

    /**
     * 企业详情（含标签和挂链节点）。
     *
     * @param id 企业 ID
     * @return 企业详情
     */
    EnterpriseDetailVO getById(Long id);

    /**
     * 新增企业（默认未入库状态）。
     *
     * @param request 创建请求
     * @return 企业详情
     */
    EnterpriseDetailVO create(CreateEnterpriseRequest request);

    /**
     * 编辑企业。
     *
     * @param id      企业 ID
     * @param request 更新请求
     * @return 企业详情
     */
    EnterpriseDetailVO update(Long id, UpdateEnterpriseRequest request);

    /**
     * 删除企业（仅未入库状态可删）。
     *
     * @param id 企业 ID
     */
    void delete(Long id);

    /**
     * 企业入库（UNREGISTERED/OUTBOUND → ACTIVE）。
     *
     * @param id 企业 ID
     */
    void inbound(Long id);

    /**
     * 企业出库（ACTIVE → OUTBOUND）。
     *
     * @param id 企业 ID
     */
    void outbound(Long id);

    /**
     * 批量入库（处理 UNREGISTERED 与 OUTBOUND 状态）。
     *
     * @param ids 企业 ID 列表
     * @return 成功入库数量
     */
    int batchInbound(List<Long> ids);

    /**
     * 批量出库。
     *
     * @param ids 企业 ID 列表
     */
    void batchOutbound(List<Long> ids);

    /**
     * 为企业绑定标签。
     *
     * @param enterpriseId 企业 ID
     * @param tagId        标签 ID
     */
    void bindTag(Long enterpriseId, Long tagId);

    /**
     * 为企业解绑标签。
     *
     * @param enterpriseId 企业 ID
     * @param tagId        标签 ID
     */
    void unbindTag(Long enterpriseId, Long tagId);
}
