package git.ggwzz.chain.mapper;

import git.ggwzz.chain.vo.ChainOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产业链选项查询 Mapper，只查询 ID 与产业链名称。
 */
@Mapper
public interface ChainOptionMapper {

    /**
     * 查询全部未删除产业链选项，按 ID 倒序。
     *
     * @return 产业链选项列表
     */
    @Select("SELECT id, chain_name AS name FROM chain "
            + "WHERE is_deleted = 0 ORDER BY id DESC")
    List<ChainOptionVO> listOptions();
}
