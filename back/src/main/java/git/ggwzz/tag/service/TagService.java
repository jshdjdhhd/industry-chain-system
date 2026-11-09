package git.ggwzz.tag.service;

import git.ggwzz.tag.dto.CreateTagRequest;
import git.ggwzz.tag.dto.TagQueryRequest;
import git.ggwzz.tag.dto.UpdateTagRequest;
import git.ggwzz.tag.vo.TagVO;

import java.util.List;

/**
 * 标签管理服务。
 */
public interface TagService {

    /**
     * 标签列表（支持关键词和类型筛选）。
     *
     * @param query 查询条件
     * @return 标签列表
     */
    List<TagVO> list(TagQueryRequest query);

    /**
     * 创建标签。
     *
     * @param request 创建请求
     * @return 标签 VO
     */
    TagVO create(CreateTagRequest request);

    /**
     * 更新标签。
     *
     * @param id      标签 ID
     * @param request 更新请求
     * @return 标签 VO
     */
    TagVO update(Long id, UpdateTagRequest request);

    /**
     * 删除标签（有企业使用时禁止删除）。
     *
     * @param id 标签 ID
     */
    void delete(Long id);
}
