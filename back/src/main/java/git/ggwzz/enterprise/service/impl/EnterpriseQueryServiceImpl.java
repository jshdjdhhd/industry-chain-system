package git.ggwzz.enterprise.service.impl;

import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.enterprise.mapper.projection.EnterpriseTagTextRow;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 企业查询服务实现。
 *
 * <p>为智能挂链推荐提供企业数据查询能力。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseQueryServiceImpl implements EnterpriseQueryService {

    private final EnterpriseMapper enterpriseMapper;

    @Override
    public RuleBasedMatchingProvider.EnterpriseInfo getById(Long id) {
        return getByIds(List.of(id)).get(id);
    }

    @Override
    public Map<Long, RuleBasedMatchingProvider.EnterpriseInfo> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Long> normalizedIds = ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        List<RuleBasedMatchingProvider.EnterpriseInfo> enterprises =
                enterpriseMapper.selectMatchingInfoByIds(new ArrayList<>(normalizedIds));
        if (enterprises == null || enterprises.isEmpty()) {
            return Map.of();
        }

        Map<Long, RuleBasedMatchingProvider.EnterpriseInfo> enterpriseMap = enterprises.stream()
                .filter(enterprise -> enterprise.getId() != null)
                .collect(Collectors.toMap(
                        RuleBasedMatchingProvider.EnterpriseInfo::getId,
                        enterprise -> enterprise,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<EnterpriseTagTextRow> tagRows =
                enterpriseMapper.selectTagTextsByEnterpriseIds(new ArrayList<>(enterpriseMap.keySet()));
        Map<Long, List<String>> tagTextsByEnterpriseId = tagRows == null
                ? Map.of()
                : tagRows.stream()
                        .filter(row -> row.getEnterpriseId() != null && row.getTagName() != null)
                        .collect(Collectors.groupingBy(
                                EnterpriseTagTextRow::getEnterpriseId,
                                LinkedHashMap::new,
                                Collectors.mapping(EnterpriseTagTextRow::getTagName, Collectors.toList())));
        enterpriseMap.forEach((enterpriseId, enterprise) ->
                enterprise.setTags(String.join(",",
                        tagTextsByEnterpriseId.getOrDefault(enterpriseId, List.of()))));
        return enterpriseMap;
    }
}
