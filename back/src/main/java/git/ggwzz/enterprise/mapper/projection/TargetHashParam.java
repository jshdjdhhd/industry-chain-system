package git.ggwzz.enterprise.mapper.projection;

/**
 * 目标企业文本哈希查询参数。
 */
public record TargetHashParam(Long targetEnterpriseId, String targetTextHash) {
}
