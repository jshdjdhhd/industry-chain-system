-- =====================================================================
-- 智能挂链 V2.1 质量优化表
-- 版本: V8
-- 数据库: MySQL 5.7 | 引擎: InnoDB | 字符集: utf8mb4
-- 说明: 增加领域词库和离线评估样本，支撑 embedding 画像优化和效果评估。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `industry_term_alias` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '领域词ID',
    `source_term`    VARCHAR(100)    NOT NULL COMMENT '原始词/企业简称/行业词',
    `expanded_terms` VARCHAR(1000)   NOT NULL COMMENT '扩展词，多个词用逗号分隔',
    `industry`       VARCHAR(100)    NULL COMMENT '所属行业',
    `priority`       INT             NOT NULL DEFAULT 0 COMMENT '优先级，值越大越优先',
    `enabled`        TINYINT         NOT NULL DEFAULT 1 COMMENT '是否启用：0-停用，1-启用',
    `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_industry_term_alias_source` (`source_term`, `is_deleted`),
    KEY `idx_industry_term_alias_enabled` (`enabled`, `is_deleted`),
    KEY `idx_industry_term_alias_industry` (`industry`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '产业领域词扩展表';

CREATE TABLE IF NOT EXISTS `matching_evaluation_sample` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评估样本ID',
    `sample_name`      VARCHAR(200)    NOT NULL COMMENT '样本名称',
    `enterprise_id`    BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `chain_id`         BIGINT UNSIGNED NOT NULL COMMENT '产业链ID',
    `expected_node_id` BIGINT UNSIGNED NOT NULL COMMENT '人工标注的正确节点ID',
    `reason`           VARCHAR(1000)   NULL COMMENT '人工标注理由',
    `enabled`          TINYINT         NOT NULL DEFAULT 1 COMMENT '是否启用：0-停用，1-启用',
    `create_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_matching_eval_chain` (`chain_id`, `enabled`, `is_deleted`),
    KEY `idx_matching_eval_enterprise` (`enterprise_id`),
    KEY `idx_matching_eval_expected_node` (`expected_node_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '智能挂链评估样本表';

INSERT INTO `industry_term_alias` (`source_term`, `expanded_terms`, `industry`, `priority`)
SELECT '中际旭创', '光模块,高速光模块,光通信,光器件,光收发模块,数据中心互联', '光通信', 100
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `industry_term_alias` WHERE `source_term` = '中际旭创' AND `is_deleted` = 0);

INSERT INTO `industry_term_alias` (`source_term`, `expanded_terms`, `industry`, `priority`)
SELECT '旭创', '光模块,高速光模块,光通信,光器件,光收发模块', '光通信', 95
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `industry_term_alias` WHERE `source_term` = '旭创' AND `is_deleted` = 0);

INSERT INTO `industry_term_alias` (`source_term`, `expanded_terms`, `industry`, `priority`)
SELECT '苹果', '手机,智能手机,iPhone,消费电子,移动终端', '消费电子', 90
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `industry_term_alias` WHERE `source_term` = '苹果' AND `is_deleted` = 0);

INSERT INTO `industry_term_alias` (`source_term`, `expanded_terms`, `industry`, `priority`)
SELECT '宁德时代', '动力电池,锂电池,电芯,电池模组,储能电池', '新能源', 90
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `industry_term_alias` WHERE `source_term` = '宁德时代' AND `is_deleted` = 0);

INSERT INTO `industry_term_alias` (`source_term`, `expanded_terms`, `industry`, `priority`)
SELECT '比亚迪', '新能源汽车,动力电池,整车制造,电驱系统', '新能源车', 85
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `industry_term_alias` WHERE `source_term` = '比亚迪' AND `is_deleted` = 0);

INSERT INTO `industry_term_alias` (`source_term`, `expanded_terms`, `industry`, `priority`)
SELECT '小米', '智能手机,消费电子,IoT终端,智能硬件', '消费电子', 80
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `industry_term_alias` WHERE `source_term` = '小米' AND `is_deleted` = 0);
