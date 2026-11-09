package git.ggwzz.enterprise.entity;

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
 * 企业相似度缓存实体，对应表 {@code enterprise_similarity_cache}。
 */
@Data
@TableName("enterprise_similarity_cache")
public class EnterpriseSimilarityCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sourceEnterpriseId;

    private Long targetEnterpriseId;

    private String modelName;

    private String sourceTextHash;

    private String targetTextHash;

    private BigDecimal similarity;

    private String reasons;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
