-- =====================================================================
-- 智能挂链推荐结果缓存
-- 版本: V23
-- 数据库: MySQL 5.7 | 引擎: InnoDB | 字符集: utf8mb4
-- 说明: 保存预热后的企业-产业链推荐结果，供批量挂链秒级复用。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `matching_recommendation_cache` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '缓存ID',
    `enterprise_id`        BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `chain_id`             BIGINT UNSIGNED NOT NULL COMMENT '产业链ID',
    `model_name`           VARCHAR(100)    NOT NULL COMMENT '模型名称',
    `enterprise_text_hash` CHAR(64)        NOT NULL COMMENT '企业画像文本SHA-256',
    `chain_nodes_hash`     CHAR(64)        NOT NULL COMMENT '产业链节点画像集合SHA-256',
    `top_k`                INT             NOT NULL COMMENT '推荐TopK',
    `vector_weight`        DECIMAL(6,4)    NOT NULL COMMENT '向量权重',
    `rule_weight`          DECIMAL(6,4)    NOT NULL COMMENT '规则权重',
    `result_json`          LONGTEXT        NOT NULL COMMENT '推荐结果JSON',
    `status`               VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '缓存状态',
    `computed_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
    `expire_at`            DATETIME        NULL COMMENT '过期时间',
    `create_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`           TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_matching_recommendation_fingerprint` (
        `enterprise_id`, `chain_id`, `model_name`, `enterprise_text_hash`, `chain_nodes_hash`,
        `top_k`, `vector_weight`, `rule_weight`, `status`, `is_deleted`
    ),
    KEY `idx_matching_recommendation_computed` (`computed_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '智能挂链推荐结果缓存表';
