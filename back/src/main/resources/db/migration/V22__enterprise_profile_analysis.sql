CREATE TABLE IF NOT EXISTS `enterprise_similarity_cache` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '缓存ID',
    `source_enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '源企业ID',
    `target_enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '目标企业ID',
    `model_name` VARCHAR(100) NOT NULL COMMENT 'embedding模型名称',
    `source_text_hash` CHAR(64) NOT NULL COMMENT '源企业画像文本SHA-256',
    `target_text_hash` CHAR(64) NOT NULL COMMENT '目标企业画像文本SHA-256',
    `similarity` DECIMAL(6,5) NOT NULL COMMENT '相似度',
    `reasons` VARCHAR(1000) NULL COMMENT '相似原因JSON',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_similarity_pair_model_hash`
        (`source_enterprise_id`, `target_enterprise_id`, `model_name`, `source_text_hash`, `target_text_hash`),
    KEY `idx_enterprise_similarity_source_model`
        (`source_enterprise_id`, `model_name`, `source_text_hash`, `is_deleted`, `similarity`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '企业相似度缓存表';
