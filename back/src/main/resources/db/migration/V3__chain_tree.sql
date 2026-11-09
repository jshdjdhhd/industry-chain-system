-- =====================================================================
-- 产业链管理系统 - 节点树形结构扩展（chain 模块，成员 C 负责）
-- 版本: V3
-- 数据库: MySQL 5.7 | 引擎: InnoDB | 字符集: utf8mb4
-- 说明: 给 V2__demo_business.sql 已建的 chain_node 表补 parent_id / node_path / sort_order
--       三列，用于满足 CLAUDE.md §86/§157 的树形规范（parent_id + node_path + 后端组装）。
--       不删不改已有演示数据，仅 ALTER 加列 + 回填 node_path。
--       另含 operation_log 表 DDL（本地开发兜底，幂等；@OperationLog 切面落库需要）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. chain_node 加列：parent_id / node_path / sort_order
-- ---------------------------------------------------------------------
ALTER TABLE `chain_node`
    ADD COLUMN `parent_id`  BIGINT UNSIGNED NULL     COMMENT '父节点 ID，NULL 为一级节点' AFTER `chain_id`,
    ADD COLUMN `node_path`  VARCHAR(255)    NOT NULL DEFAULT '/' COMMENT '节点路径，如 /5/6/7/，用于子树查询' AFTER `node_level`,
    ADD COLUMN `sort_order` INT             NOT NULL DEFAULT 0  COMMENT '同级排序' AFTER `node_path`;

-- ---------------------------------------------------------------------
-- 2. 回填已有演示节点的 node_path（parent_id 留 NULL，演示数据本就不是树）
--    /id/ 形式，便于后续 LIKE 前缀查询
-- ---------------------------------------------------------------------
UPDATE `chain_node`
SET `node_path` = CONCAT('/', `id`, '/')
WHERE `node_path` = '/' OR `node_path` IS NULL;

-- ---------------------------------------------------------------------
-- 3. 树形查询索引
-- ---------------------------------------------------------------------
ALTER TABLE `chain_node`
    ADD KEY `idx_chain_node_parent_id` (`parent_id`),
    ADD KEY `idx_chain_node_node_path` (`node_path`);

-- ---------------------------------------------------------------------
-- 4. 操作日志表（@OperationLog 切面落库，字段对齐 SysOperationLog）
--    V1__sys_auth.sql 不建此表（由项目方建库时建），此处幂等兜底，便于本地开发。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `operator`    VARCHAR(64)     DEFAULT NULL            COMMENT '操作人',
    `action`      VARCHAR(64)     DEFAULT NULL            COMMENT '业务动作',
    `target_type` VARCHAR(64)     DEFAULT NULL            COMMENT '目标类型',
    `target_id`   BIGINT UNSIGNED DEFAULT NULL            COMMENT '目标 ID',
    `detail`      TEXT            DEFAULT NULL            COMMENT '详情 JSON',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_operation_log_operator` (`operator`),
    KEY `idx_operation_log_action`   (`action`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志表';
