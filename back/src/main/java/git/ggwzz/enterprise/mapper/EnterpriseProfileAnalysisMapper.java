package git.ggwzz.enterprise.mapper;

import git.ggwzz.enterprise.mapper.projection.EnterpriseLinkStatRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseTagTextRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业画像分析查询 Mapper。
 */
@Mapper
public interface EnterpriseProfileAnalysisMapper {

    EnterpriseProfileAnalysisRow selectProfileById(@Param("id") Long id);

    List<EnterpriseProfileAnalysisRow> selectActiveCandidates(
            @Param("excludeEnterpriseId") Long excludeEnterpriseId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    List<EnterpriseProfileAnalysisRow> selectSameIndustryActiveCandidates(
            @Param("industry") String industry,
            @Param("excludeEnterpriseId") Long excludeEnterpriseId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    List<String> selectTagTextsByEnterpriseId(@Param("enterpriseId") Long enterpriseId);

    List<EnterpriseTagTextRow> selectTagTextsByEnterpriseIds(
            @Param("enterpriseIds") List<Long> enterpriseIds);

    List<EnterpriseLinkStatRow> selectLinkStatsByEnterpriseIds(
            @Param("enterpriseIds") List<Long> enterpriseIds);
}
