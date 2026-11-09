-- =====================================================================
-- 产业链管理系统 - 健康分析报告任务与内容表
-- 版本: V11
-- 数据库: MySQL 5.7 | 引擎: InnoDB | 字符集: utf8mb4
-- 说明:
--   1. analysis_report_task 报告任务表（追加式历史，不做逻辑删除）
--   2. analysis_report_content 报告内容表（与任务 1:1，存 Markdown/HTML/PDF 路径/指标快照）
-- =====================================================================

CREATE TABLE IF NOT EXISTS `analysis_report_task` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '报告任务ID',
    `chain_id` BIGINT UNSIGNED NOT NULL COMMENT '产业链ID',
    `report_type` VARCHAR(32) NOT NULL COMMENT '报告类型',
    `scope_type` VARCHAR(32) NOT NULL COMMENT '范围类型',
    `selected_node_ids` JSON NULL COMMENT '用户选择节点ID JSON',
    `resolved_node_ids` JSON NULL COMMENT '展开子树后的实际节点ID JSON',
    `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '任务状态',
    `retry_from_task_id` BIGINT UNSIGNED NULL COMMENT '重试来源任务ID',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `input_snapshot` JSON NULL COMMENT '请求参数和指标快照',
    `error_message` VARCHAR(1000) NULL COMMENT '失败原因',
    `created_by` VARCHAR(64) NULL COMMENT '创建人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `completed_at` DATETIME NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_analysis_report_task_chain_id` (`chain_id`),
    KEY `idx_analysis_report_task_status` (`status`),
    KEY `idx_analysis_report_task_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '健康分析报告任务表';

CREATE TABLE IF NOT EXISTS `analysis_report_content` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内容ID',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '报告任务ID',
    `title` VARCHAR(200) NOT NULL COMMENT '报告标题',
    `content_markdown` MEDIUMTEXT NULL COMMENT 'Markdown源内容',
    `content_html` MEDIUMTEXT NULL COMMENT 'HTML预览内容',
    `pdf_file_path` VARCHAR(500) NULL COMMENT 'PDF文件路径',
    `metrics_snapshot` JSON NULL COMMENT '健康指标快照',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_analysis_report_content_task_id` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '健康分析报告内容表';
