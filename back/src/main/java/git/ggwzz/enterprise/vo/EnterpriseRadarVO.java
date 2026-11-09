package git.ggwzz.enterprise.vo;

import lombok.Data;

import java.util.List;

/**
 * 企业雷达画像 VO。
 */
@Data
public class EnterpriseRadarVO {

    private List<EnterpriseRadarMetricVO> metrics;
}
