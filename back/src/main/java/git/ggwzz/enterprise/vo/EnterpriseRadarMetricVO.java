package git.ggwzz.enterprise.vo;

import lombok.Data;

/**
 * 企业雷达指标 VO。
 */
@Data
public class EnterpriseRadarMetricVO {

    private String key;

    private String name;

    private Integer value;

    private String rawValue;

    private String description;
}
