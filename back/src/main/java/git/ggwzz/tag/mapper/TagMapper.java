package git.ggwzz.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.tag.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签 Mapper。
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
