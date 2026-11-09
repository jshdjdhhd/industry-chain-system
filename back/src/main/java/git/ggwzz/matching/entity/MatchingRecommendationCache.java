package git.ggwzz.matching.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能挂链推荐结果缓存，对应表 {@code matching_recommendation_cache}。
 */
@Data
@TableName("matching_recommendation_cache")
public class MatchingRecommendationCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;

    private Long chainId;

    private String modelName;

    private String enterpriseTextHash;

    private String chainNodesHash;

    private Integer topK;

    private BigDecimal vectorWeight;

    private BigDecimal ruleWeight;

    private String resultJson;

    private String status;

    private LocalDateTime computedAt;

    private LocalDateTime expireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
