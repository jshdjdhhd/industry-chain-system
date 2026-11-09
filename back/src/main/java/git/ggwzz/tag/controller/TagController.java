package git.ggwzz.tag.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.tag.dto.CreateTagRequest;
import git.ggwzz.tag.dto.TagQueryRequest;
import git.ggwzz.tag.dto.UpdateTagRequest;
import git.ggwzz.tag.service.TagService;
import git.ggwzz.tag.vo.TagVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签管理接口。
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    @PreAuthorize("hasAuthority('enterprise:tag:query')")
    public Result<List<TagVO>> list(TagQueryRequest query) {
        return Result.success(tagService.list(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('enterprise:tag:create')")
    @OperationLog(action = "create_tag", targetType = "tag", detail = "创建标签")
    public Result<TagVO> create(@Valid @RequestBody CreateTagRequest request) {
        return Result.success(tagService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('enterprise:tag:update')")
    @OperationLog(action = "update_tag", targetType = "tag", targetId = "#id", detail = "更新标签")
    public Result<TagVO> update(@PathVariable Long id, @Valid @RequestBody UpdateTagRequest request) {
        return Result.success(tagService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('enterprise:tag:delete')")
    @OperationLog(action = "delete_tag", targetType = "tag", targetId = "#id", detail = "删除标签")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.success();
    }
}
