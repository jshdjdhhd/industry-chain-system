-- =====================================================================
-- 产业链管理系统 - 企业与标签模块
-- 版本: V5
-- 数据库: MySQL 5.7  | 引擎: InnoDB | 字符集: utf8mb4 | 排序: utf8mb4_general_ci
-- 说明: 企业表（扩展）、标签表、企业-标签关联表。
--       禁止外键、级联；删除一律逻辑删除；无 MySQL 8 专属语法。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 企业表（扩展版，覆盖原 demo 中的简版 enterprise 表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '企业ID',
    `enterprise_name`   VARCHAR(200)    NOT NULL COMMENT '企业名称',
    `unified_code`      VARCHAR(32)     NULL     COMMENT '统一社会信用代码',
    `legal_person`      VARCHAR(64)     NULL     COMMENT '法定代表人',
    `region`            VARCHAR(64)     NULL     COMMENT '所在地区',
    `industry`          VARCHAR(64)     NULL     COMMENT '所属行业',
    `address`           VARCHAR(255)    NULL     COMMENT '经营地址',
    `registered_capital` VARCHAR(64)    NULL     COMMENT '注册资本',
    `establish_date`    DATE            NULL     COMMENT '成立日期',
    `business_scope`    TEXT            NULL     COMMENT '经营范围',
    `main_products`     TEXT            NULL     COMMENT '主营产品',
    `core_tech`         TEXT            NULL     COMMENT '核心技术',
    `patent_info`       TEXT            NULL     COMMENT '专利信息',
    `bid_info`          TEXT            NULL     COMMENT '招投标信息',
    `project_info`      TEXT            NULL     COMMENT '项目信息',
    `contact_person`    VARCHAR(64)     NULL     COMMENT '联系人',
    `contact_phone`     VARCHAR(32)     NULL     COMMENT '联系方式',
    `keywords`          VARCHAR(500)    NULL     COMMENT '核心关键词（逗号分隔）',
    `output_value`      DECIMAL(18,2)   NULL     COMMENT '产值（万元）',
    `tax_amount`        DECIMAL(18,2)   NULL     COMMENT '税收（万元）',
    `status`            VARCHAR(20)     NOT NULL DEFAULT 'UNREGISTERED' COMMENT '状态：UNREGISTERED-未入库/ACTIVE-已入库',
    `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`        TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_unified_code` (`unified_code`),
    KEY `idx_enterprise_status` (`status`),
    KEY `idx_enterprise_industry` (`industry`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '企业表';

-- ---------------------------------------------------------------------
-- 标签表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tag` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `tag_name`    VARCHAR(64)     NOT NULL COMMENT '标签名称',
    `tag_type`    VARCHAR(32)     NOT NULL DEFAULT 'CUSTOM' COMMENT '标签类型：INDUSTRY-产业标签/SCALE-规模标签/RISK-风险标签/CUSTOM-自定义标签',
    `color`       VARCHAR(16)     NULL     COMMENT '颜色（十六进制）',
    `description` VARCHAR(255)    NULL     COMMENT '标签说明',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_tag_type` (`tag_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签表';

-- ---------------------------------------------------------------------
-- 企业-标签关联表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise_tag_rel` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `tag_id`        BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_tag` (`enterprise_id`, `tag_id`),
    KEY `idx_enterprise_tag_rel_eid` (`enterprise_id`),
    KEY `idx_enterprise_tag_rel_tid` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '企业-标签关联表';
