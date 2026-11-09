package git.ggwzz.enterprise.service;

import git.ggwzz.enterprise.dto.VectorWarmupRequest;
import git.ggwzz.enterprise.vo.EnterpriseProfileAnalysisVO;
import git.ggwzz.enterprise.vo.EnterpriseRadarVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;

/**
 * 企业画像分析聚合服务。
 */
public interface EnterpriseProfileAnalysisService {

    EnterpriseProfileAnalysisVO analyze(Long enterpriseId, Integer similarLimit);

    EnterpriseRadarVO analyzeRadar(Long enterpriseId);

    VectorWarmupResultVO warmupVectors(VectorWarmupRequest request);
}
