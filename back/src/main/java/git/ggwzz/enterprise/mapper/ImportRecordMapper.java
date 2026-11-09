package git.ggwzz.enterprise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.enterprise.entity.ImportRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导入记录 Mapper 接口
 */
@Mapper
public interface ImportRecordMapper extends BaseMapper<ImportRecord> {
}
