package git.ggwzz.tag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.entity.EnterpriseTagRel;
import git.ggwzz.enterprise.mapper.EnterpriseTagRelMapper;
import git.ggwzz.tag.dto.CreateTagRequest;
import git.ggwzz.tag.dto.TagQueryRequest;
import git.ggwzz.tag.dto.UpdateTagRequest;
import git.ggwzz.tag.entity.Tag;
import git.ggwzz.tag.mapper.TagMapper;
import git.ggwzz.tag.service.TagService;
import git.ggwzz.tag.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标签管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final EnterpriseTagRelMapper enterpriseTagRelMapper;

    @Override
    public List<TagVO> list(TagQueryRequest query) {
        if (query == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getKeyword())) {
            wrapper.like(Tag::getTagName, query.getKeyword());
        }
        if (StringUtils.isNotBlank(query.getType())) {
            wrapper.eq(Tag::getTagType, query.getType());
        }
        wrapper.orderByDesc(Tag::getCreateTime);

        List<Tag> tags = tagMapper.selectList(wrapper);
        if (tags.isEmpty()) {
            return List.of();
        }

        // 标签列表同时展示引用量；先取得标签集合，再从企业标签关系表补充 usedCount。
        List<Long> tagIds = tags.stream().map(Tag::getId).collect(Collectors.toList());
        Map<Long, Long> usedCountMap = countUsedByTagIds(tagIds);

        return tags.stream().map(tag -> toVO(tag, usedCountMap.getOrDefault(tag.getId(), 0L).intValue()))
                .collect(Collectors.toList());
    }

    @Override
    public TagVO create(CreateTagRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        Tag tag = new Tag();
        tag.setTagName(request.getTagName());
        tag.setTagType(request.getTagType());
        tag.setColor(request.getColor());
        tag.setDescription(request.getDescription());
        tagMapper.insert(tag);
        return toVO(tag, 0);
    }

    @Override
    public TagVO update(Long id, UpdateTagRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在");
        }
        if (request.getTagName() != null) {
            tag.setTagName(request.getTagName());
        }
        if (request.getTagType() != null) {
            tag.setTagType(request.getTagType());
        }
        if (request.getColor() != null) {
            tag.setColor(request.getColor());
        }
        if (request.getDescription() != null) {
            tag.setDescription(request.getDescription());
        }
        tagMapper.updateById(tag);

        // 查询使用次数
        Long usedCount = countUsedByTagId(id);
        return toVO(tag, usedCount.intValue());
    }

    @Override
    public void delete(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在");
        }
        // 删除前检查引用关系，避免企业详情出现指向已删除标签的悬空数据。
        Long usedCount = countUsedByTagId(id);
        if (usedCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该标签已被企业使用，需先解绑后删除");
        }
        tagMapper.deleteById(id);
    }

    /**
     * 统计单个标签的使用次数。
     */
    private Long countUsedByTagId(Long tagId) {
        LambdaQueryWrapper<EnterpriseTagRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnterpriseTagRel::getTagId, tagId);
        return enterpriseTagRelMapper.selectCount(wrapper);
    }

    /**
     * 批量统计标签使用次数。
     */
    private Map<Long, Long> countUsedByTagIds(List<Long> tagIds) {
        // 当前标签规模较小，逐项复用单标签统计；数据量增长后可替换为 GROUP BY 聚合查询。
        return tagIds.stream()
                .collect(Collectors.toMap(id -> id, this::countUsedByTagId));
    }

    /**
     * 实体转 VO。
     */
    private TagVO toVO(Tag tag, int usedCount) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getTagName());
        vo.setType(tag.getTagType());
        vo.setColor(tag.getColor());
        vo.setDescription(tag.getDescription());
        vo.setUsedCount(usedCount);
        return vo;
    }
}
