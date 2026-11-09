-- =====================================================================
-- 产业链管理系统 - 企业导入记录表
-- 版本: V7
-- 数据库: MySQL 5.7  | 引擎: InnoDB | 字符集: utf8mb4 | 排序: utf8mb4_general_ci
-- 说明: 记录企业批量导入的历史、成功/失败数量及失败明细。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `import_record` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `file_name`       VARCHAR(255)    NOT NULL COMMENT '文件名称',
    `operator`        VARCHAR(64)     NOT NULL COMMENT '操作人',
    `total_count`     INT             NOT NULL DEFAULT 0 COMMENT '总行数',
    `success_count`   INT             NOT NULL DEFAULT 0 COMMENT '成功数',
    `failure_count`   INT             NOT NULL DEFAULT 0 COMMENT '失败数',
    `status`          VARCHAR(20)     NOT NULL COMMENT '状态：SUCCESS-成功/PARTIAL_FAILED-部分失败/FAILED-失败',
    `failure_detail`  TEXT            NULL     COMMENT '失败明细JSON',
    `imported_at`     DATETIME        NOT NULL COMMENT '导入时间',
    PRIMARY KEY (`id`),
    KEY `idx_import_record_status` (`status`),
    KEY `idx_import_record_imported_at` (`imported_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '企业导入记录表';
