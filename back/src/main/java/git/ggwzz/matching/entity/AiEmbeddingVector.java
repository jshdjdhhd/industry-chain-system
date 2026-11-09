package git.ggwzz.matching.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 向量缓存实体，对应表 {@code ai_embedding_vector}。
 */
@Data
@TableName("ai_embedding_vector")
public class AiEmbeddingVector {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String objectType;

    private Long objectId;

    private String modelName;

    private Integer dimension;

    private String textHash;

    private String vectorData;

    private String sourceTextSnapshot;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
