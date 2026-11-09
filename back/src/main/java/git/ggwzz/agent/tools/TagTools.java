package git.ggwzz.agent.tools;

import git.ggwzz.enterprise.service.EnterpriseService;
import git.ggwzz.tag.dto.CreateTagRequest;
import git.ggwzz.tag.dto.TagQueryRequest;
import git.ggwzz.tag.service.TagService;
import git.ggwzz.tag.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 标签相关工具集，供 AI Agent 调用。
 */
@Component
@RequiredArgsConstructor
public class TagTools {

    private final TagService tagService;
    private final EnterpriseService enterpriseService;

    @Tool(description = "列出标签，可按类型筛选。返回标签列表（ID、名称、类型、颜色、使用数量）。")
    public String listTags(
            @ToolParam(description = "标签类型筛选，为空则返回所有类型", required = false) String type) {
        try {
            TagQueryRequest query = new TagQueryRequest();
            query.setType(type);
            List<TagVO> tags = tagService.list(query);
            if (tags.isEmpty()) {
                return "没有标签";
            }
            return tags.stream()
                    .map(t -> String.format("ID=%d, 名称=%s, 类型=%s, 颜色=%s, 使用次数=%d",
                            t.getId(), t.getName(), t.getType(),
                            t.getColor() != null ? t.getColor() : "无",
                            t.getUsedCount() != null ? t.getUsedCount() : 0))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "获取标签列表失败: " + e.getMessage();
        }
    }

    @Tool(description = "创建新标签。需要指定标签名称和类型。")
    public String createTag(
            @ToolParam(description = "标签名称") String name,
            @ToolParam(description = "标签类型（如：行业、规模、状态等）") String type,
            @ToolParam(description = "标签颜色（十六进制色值）", required = false) String color,
            @ToolParam(description = "标签说明", required = false) String description) {
        try {
            CreateTagRequest request = new CreateTagRequest();
            request.setTagName(name);
            request.setTagType(type);
            request.setColor(color);
            request.setDescription(description);
            TagVO tag = tagService.create(request);
            return String.format("标签创建成功: ID=%d, 名称=%s, 类型=%s", tag.getId(), tag.getName(), tag.getType());
        } catch (Exception e) {
            return "创建标签失败: " + e.getMessage();
        }
    }

    @Tool(description = "为企业绑定标签。")
    public String bindTag(
            @ToolParam(description = "企业ID") Long enterpriseId,
            @ToolParam(description = "标签ID") Long tagId) {
        try {
            enterpriseService.bindTag(enterpriseId, tagId);
            return String.format("标签绑定成功: 企业ID=%d, 标签ID=%d", enterpriseId, tagId);
        } catch (Exception e) {
            return "绑定标签失败: " + e.getMessage();
        }
    }

    @Tool(description = "为企业解绑标签。")
    public String unbindTag(
            @ToolParam(description = "企业ID") Long enterpriseId,
            @ToolParam(description = "标签ID") Long tagId) {
        try {
            enterpriseService.unbindTag(enterpriseId, tagId);
            return String.format("标签解绑成功: 企业ID=%d, 标签ID=%d", enterpriseId, tagId);
        } catch (Exception e) {
            return "解绑标签失败: " + e.getMessage();
        }
    }
}
