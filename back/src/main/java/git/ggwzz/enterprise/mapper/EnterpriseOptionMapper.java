package git.ggwzz.enterprise.mapper;

import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 企业选项查询 Mapper。
 *
 * <p>只查询 ID 与企业名称，按名称前缀过滤，避免加载企业实体的大文本字段。</p>
 */
@Mapper
public interface EnterpriseOptionMapper {

    /**
     * 按名称前缀搜索 ACTIVE 企业选项。
     *
     * @param keyword 关键词，仅匹配企业名称前缀；可为 null 或空
     * @param limit   最大返回条数
     * @return 企业选项列表
     */
    @Select("<script>"
            + "SELECT id, enterprise_name AS name FROM enterprise "
            + "WHERE is_deleted = 0 AND status = 'ACTIVE' "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND enterprise_name LIKE CONCAT(#{keyword}, '%') "
            + "</if>"
            + "ORDER BY create_time DESC, id DESC "
            + "LIMIT #{limit}"
            + "</script>")
    List<EnterpriseOptionVO> searchActiveOptions(
            @Param("keyword") String keyword,
            @Param("limit") int limit);
}
