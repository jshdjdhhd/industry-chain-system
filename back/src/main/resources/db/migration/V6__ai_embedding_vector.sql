-- =====================================================================
-- AI embedding 向量缓存
-- 版本: V6
-- 数据库: MySQL 5.7 | 引擎: InnoDB | 字符集: utf8mb4
-- 说明: MySQL 5.7 无原生向量类型，使用 LONGTEXT 存储 JSON 数组。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `ai_embedding_vector` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '向量ID',
    `object_type`          VARCHAR(32)     NOT NULL COMMENT '对象类型：ENTERPRISE/CHAIN_NODE',
    `object_id`            BIGINT UNSIGNED NOT NULL COMMENT '对象ID',
    `model_name`           VARCHAR(100)    NOT NULL COMMENT 'embedding模型名称',
    `dimension`            INT             NOT NULL COMMENT '向量维度',
    `text_hash`            CHAR(64)        NOT NULL COMMENT '画像文本SHA-256',
    `vector_data`          LONGTEXT        NOT NULL COMMENT '向量JSON数组',
    `source_text_snapshot` LONGTEXT        NOT NULL COMMENT '生成向量时使用的画像文本快照',
    `status`               VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `create_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`           TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_ai_embedding_object` (`object_type`, `object_id`, `model_name`, `is_deleted`),
    KEY `idx_ai_embedding_hash` (`text_hash`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI embedding向量缓存表';
