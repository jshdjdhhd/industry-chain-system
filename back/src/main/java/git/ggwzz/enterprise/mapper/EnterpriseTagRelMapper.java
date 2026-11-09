package git.ggwzz.enterprise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.enterprise.entity.EnterpriseTagRel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 企业-标签关联 Mapper。
 */
@Mapper
public interface EnterpriseTagRelMapper extends BaseMapper<EnterpriseTagRel> {
}
