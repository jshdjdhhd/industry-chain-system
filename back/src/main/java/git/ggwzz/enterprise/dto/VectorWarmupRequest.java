package git.ggwzz.enterprise.dto;

import lombok.Data;

import java.util.List;

/**
 * 企业画像向量预热请求。
 */
@Data
public class VectorWarmupRequest {

    private List<Long> enterpriseIds;
}
