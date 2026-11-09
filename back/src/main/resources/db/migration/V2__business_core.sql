-- =====================================================================
-- 产业链管理系统 - 业务核心表（智能挂链/正式挂链）
-- 版本: V2
-- 数据库: MySQL 5.7  | 引擎: InnoDB | 字符集: utf8mb4 | 排序: utf8mb4_general_ci
-- 说明: 智能挂链推荐任务表、推荐结果表、企业-节点关系表。
--       禁止外键、级联；删除一律逻辑删除；无 MySQL 8 专属语法。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 智能挂链推荐任务表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `matching_task` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `enterprise_id`   BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `chain_id`        BIGINT UNSIGNED NOT NULL COMMENT '产业链ID',
    `input_snapshot`  JSON            NOT NULL COMMENT '输入快照（JSON）',
    `model_name`      VARCHAR(64)     NOT NULL COMMENT '模型名称',
    `status`          VARCHAR(32)     NOT NULL DEFAULT 'CREATED' COMMENT '任务状态',
    `created_by`      VARCHAR(64)     NULL     COMMENT '创建人ID',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_matching_task_enterprise_id` (`enterprise_id`),
    KEY `idx_matching_task_chain_id` (`chain_id`),
    KEY `idx_matching_task_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '智能挂链推荐任务表';

-- ---------------------------------------------------------------------
-- 智能挂链推荐结果表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `matching_result` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '结果ID',
    `task_id`         BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
    `node_id`         BIGINT UNSIGNED NOT NULL COMMENT '节点ID',
    `confidence`      DECIMAL(5,4)    NOT NULL COMMENT '置信度',
    `reason`          VARCHAR(1000)   NOT NULL COMMENT '推荐理由',
    `evidence`        VARCHAR(1000)   NULL     COMMENT '证据字段',
    `rank_no`         INT             NOT NULL DEFAULT 0 COMMENT '排名',
    `decision`        VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '决策状态',
    `reject_reason`   VARCHAR(500)    NULL     COMMENT '拒绝原因',
    `decided_by`      VARCHAR(64)     NULL     COMMENT '决策人ID',
    `decided_at`      DATETIME        NULL     COMMENT '决策时间',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_matching_result_task_id` (`task_id`),
    KEY `idx_matching_result_node_id` (`node_id`),
    KEY `idx_matching_result_decision` (`decision`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '智能挂链推荐结果表';

-- ---------------------------------------------------------------------
-- 企业-节点关系表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise_node_rel` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关系ID',
    `enterprise_id`   BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `node_id`         BIGINT UNSIGNED NOT NULL COMMENT '节点ID',
    `source`          VARCHAR(20)     NOT NULL COMMENT '来源（MANUAL/AI/IMPORT）',
    `confidence`      DECIMAL(5,4)    NULL     COMMENT '置信度',
    `reason`          TEXT            NULL     COMMENT '理由',
    `confirmed_by`    BIGINT UNSIGNED NULL     COMMENT '确认人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_node` (`enterprise_id`, `node_id`),
    KEY `idx_enterprise_node_rel_enterprise_id` (`enterprise_id`),
    KEY `idx_enterprise_node_rel_node_id` (`node_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '企业-节点关系表';
