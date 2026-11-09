package git.ggwzz.enterprise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.enterprise.entity.Enterprise;
import git.ggwzz.enterprise.mapper.projection.EnterpriseTagTextRow;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 企业 Mapper。
 *
 * <p>支持基础 CRUD 和智能挂链模块的企业特征查询。</p>
 */
@Mapper
public interface EnterpriseMapper extends BaseMapper<Enterprise> {

    /**
     * 查询智能挂链所需的企业特征。
     *
     * @param id 企业 ID
     * @return 企业特征，不存在时返回 null
     */
    @Select("SELECT id, enterprise_name AS name, "
            + "COALESCE(NULLIF(business_scope, ''), industry) AS business_scope, "
            + "CONCAT(IFNULL(main_products, ''), ',', IFNULL(keywords, ''), ',', IFNULL(core_tech, '')) "
            + "AS main_products "
            + "FROM enterprise WHERE id = #{id} AND status = 'ACTIVE' AND is_deleted = 0")
    RuleBasedMatchingProvider.EnterpriseInfo selectMatchingInfoById(@Param("id") Long id);

    /**
     * 批量查询智能挂链所需的企业特征。
     *
     * @param ids 企业 ID 列表
     * @return 企业特征列表
     */
    @Select("<script>"
            + "SELECT id, enterprise_name AS name, "
            + "COALESCE(NULLIF(business_scope, ''), industry) AS business_scope, "
            + "CONCAT(IFNULL(main_products, ''), ',', IFNULL(keywords, ''), ',', IFNULL(core_tech, '')) "
            + "AS main_products "
            + "FROM enterprise WHERE status = 'ACTIVE' AND is_deleted = 0 "
            + "AND id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach> "
            + "ORDER BY id"
            + "</script>")
    List<RuleBasedMatchingProvider.EnterpriseInfo> selectMatchingInfoByIds(@Param("ids") List<Long> ids);

    /**
     * 查询企业标签文本。
     *
     * @param enterpriseId 企业 ID
     * @return 标签值列表
     */
    @Select("SELECT t.tag_name FROM enterprise_tag_rel r "
            + "JOIN tag t ON t.id = r.tag_id "
            + "WHERE r.enterprise_id = #{enterpriseId} AND t.is_deleted = 0 "
            + "ORDER BY r.id")
    List<String> selectTagTextsByEnterpriseId(@Param("enterpriseId") Long enterpriseId);

    /**
     * 批量查询企业标签文本。
     *
     * @param enterpriseIds 企业 ID 列表
     * @return 企业标签文本行
     */
    @Select("<script>"
            + "SELECT r.enterprise_id AS enterpriseId, t.tag_name AS tagName "
            + "FROM enterprise_tag_rel r "
            + "JOIN tag t ON t.id = r.tag_id "
            + "WHERE t.is_deleted = 0 "
            + "AND r.enterprise_id IN "
            + "<foreach item='enterpriseId' collection='enterpriseIds' open='(' separator=',' close=')'>"
            + "#{enterpriseId}"
            + "</foreach> "
            + "ORDER BY r.enterprise_id, r.id"
            + "</script>")
    List<EnterpriseTagTextRow> selectTagTextsByEnterpriseIds(
            @Param("enterpriseIds") List<Long> enterpriseIds);
}
