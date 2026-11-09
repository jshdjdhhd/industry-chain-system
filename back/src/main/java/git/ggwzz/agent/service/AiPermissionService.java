package git.ggwzz.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import git.ggwzz.agent.entity.AiToolPermission;
import git.ggwzz.agent.mapper.AiToolPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiPermissionService {

    private final AiToolPermissionMapper mapper;

    public Map<String, Boolean> getUserOverrides(Long userId) {
        List<AiToolPermission> list = mapper.selectList(
                new LambdaQueryWrapper<AiToolPermission>()
                        .eq(AiToolPermission::getUserId, userId));
        return list.stream()
                .collect(Collectors.toMap(AiToolPermission::getToolName,
                        AiToolPermission::getEnabled, (a, b) -> b));
    }

    public void updateUserOverrides(Long userId, List<AiToolPermission> permissions) {
        for (AiToolPermission perm : permissions) {
            AiToolPermission existing = mapper.selectOne(
                    new LambdaQueryWrapper<AiToolPermission>()
                            .eq(AiToolPermission::getUserId, userId)
                            .eq(AiToolPermission::getToolName, perm.getToolName()));
            if (existing != null) {
                existing.setEnabled(perm.getEnabled());
                mapper.updateById(existing);
            } else {
                perm.setUserId(userId);
                mapper.insert(perm);
            }
        }
    }

    public List<AiToolPermission> listAll() {
        return mapper.selectList(null);
    }
}
