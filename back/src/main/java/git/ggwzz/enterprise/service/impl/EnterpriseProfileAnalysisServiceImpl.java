package git.ggwzz.enterprise.service.impl;

import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.dto.VectorWarmupRequest;
import git.ggwzz.enterprise.mapper.EnterpriseProfileAnalysisMapper;
import git.ggwzz.enterprise.mapper.projection.EnterpriseLinkStatRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.service.EnterpriseProfileAnalysisService;
import git.ggwzz.enterprise.service.EnterpriseSimilarityService;
import git.ggwzz.enterprise.vo.EnterpriseProfileAnalysisVO;
import git.ggwzz.enterprise.vo.EnterpriseRadarVO;
import git.ggwzz.enterprise.vo.SimilarEnterpriseVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 企业画像分析聚合服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseProfileAnalysisServiceImpl implements EnterpriseProfileAnalysisService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int DEFAULT_SIMILAR_LIMIT = 5;

    private final EnterpriseProfileAnalysisMapper profileAnalysisMapper;
    private final EnterpriseSimilarityService enterpriseSimilarityService;
    private final EnterpriseRadarMetricCalculator radarMetricCalculator;

    @Override
    public EnterpriseProfileAnalysisVO analyze(Long enterpriseId, Integer similarLimit) {
        if (enterpriseId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "企业ID不能为空");
        }
        int limit = similarLimit == null ? DEFAULT_SIMILAR_LIMIT : similarLimit;
        validateLimit(limit);

        EnterpriseProfileAnalysisRow profile = profileAnalysisMapper.selectProfileById(enterpriseId);
        if (profile == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        if (!STATUS_ACTIVE.equals(profile.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已入库企业支持画像分析");
        }

        List<SimilarEnterpriseVO> similarEnterprises = findSimilarEnterprises(profile, limit);

        EnterpriseProfileAnalysisVO vo = new EnterpriseProfileAnalysisVO();
        vo.setEnterpriseId(profile.getId());
        vo.setEnterpriseName(profile.getEnterpriseName());
        vo.setRadar(buildRadar(profile));
        vo.setSimilarEnterprises(similarEnterprises);
        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public VectorWarmupResultVO warmupVectors(VectorWarmupRequest request) {
        return enterpriseSimilarityService.warmupActiveEnterpriseVectors(request);
    }

    @Override
    public EnterpriseRadarVO analyzeRadar(Long enterpriseId) {
        if (enterpriseId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "企业ID不能为空");
        }
        EnterpriseProfileAnalysisRow profile = profileAnalysisMapper.selectProfileById(enterpriseId);
        if (profile == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        if (!STATUS_ACTIVE.equals(profile.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已入库企业支持画像分析");
        }
        return buildRadar(profile);
    }

    private EnterpriseRadarVO buildRadar(EnterpriseProfileAnalysisRow profile) {
        List<String> tags = profileAnalysisMapper.selectTagTextsByEnterpriseId(profile.getId());
        EnterpriseLinkStatRow linkStat = linkStat(profile.getId());
        return radarMetricCalculator.calculate(profile, tags, linkStat);
    }

    private EnterpriseLinkStatRow linkStat(Long enterpriseId) {
        List<EnterpriseLinkStatRow> rows = profileAnalysisMapper.selectLinkStatsByEnterpriseIds(List.of(enterpriseId));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<Long, EnterpriseLinkStatRow> byId = rows.stream()
                .collect(Collectors.toMap(EnterpriseLinkStatRow::getEnterpriseId,
                        Function.identity(), (left, right) -> left));
        return byId.get(enterpriseId);
    }

    private List<SimilarEnterpriseVO> findSimilarEnterprises(EnterpriseProfileAnalysisRow profile, int limit) {
        try {
            return enterpriseSimilarityService.findSimilarEnterprises(profile, limit);
        } catch (RuntimeException ex) {
            log.warn("enterprise similar analysis degraded: enterpriseId={}, reason={}",
                    profile.getId(), ex.getClass().getSimpleName());
            log.debug("enterprise similar analysis degraded detail", ex);
            return List.of();
        }
    }

    private void validateLimit(int similarLimit) {
        if (similarLimit != 5 && similarLimit != 10 && similarLimit != 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "相似企业数量只能是 5、10 或 20");
        }
    }
}
