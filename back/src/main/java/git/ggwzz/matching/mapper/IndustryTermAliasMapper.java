package git.ggwzz.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.matching.entity.IndustryTermAlias;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产业领域词别名 Mapper。
 */
@Mapper
public interface IndustryTermAliasMapper extends BaseMapper<IndustryTermAlias> {

    @Select("SELECT id, source_term, expanded_terms, industry, priority, enabled, "
            + "create_time, update_time, is_deleted "
            + "FROM industry_term_alias "
            + "WHERE enabled = 1 AND is_deleted = 0 "
            + "ORDER BY priority DESC, id ASC")
    List<IndustryTermAlias> selectEnabledAliases();
}
