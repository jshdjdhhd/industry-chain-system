package git.ggwzz.enterprise.service;

import git.ggwzz.enterprise.dto.VectorWarmupRequest;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.vo.SimilarEnterpriseVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;

import java.util.List;

/**
 * 企业相似度分析服务。
 */
public interface EnterpriseSimilarityService {

    List<SimilarEnterpriseVO> findSimilarEnterprises(EnterpriseProfileAnalysisRow source, int similarLimit);

    VectorWarmupResultVO warmupActiveEnterpriseVectors(VectorWarmupRequest request);
}
