-- =====================================================================
-- 产业链管理系统 - 鉴权与操作日志表初始化
-- 版本: V1
-- 数据库: MySQL 5.7  | 引擎: InnoDB | 字符集: utf8mb4 | 排序: utf8mb4_general_ci
-- 说明: 仅创建本工程负责的鉴权表与操作日志表；业务表由项目方另行维护。
--       禁止外键、级联；删除一律逻辑删除；无 MySQL 8 专属语法。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 系统用户表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码哈希（{bcrypt} 前缀）',
    `real_name`   VARCHAR(64)  NULL     COMMENT '真实姓名',
    `email`       VARCHAR(128) NULL     COMMENT '邮箱',
    `phone`       VARCHAR(32)  NULL     COMMENT '手机号',
    `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1-正常，2-停用，3-锁定',
    `deleted`  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '删除标识：0-未删除，1-已删除',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统用户表';

-- ---------------------------------------------------------------------
-- 系统角色表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`   VARCHAR(64)  NOT NULL COMMENT '角色编码',
    `role_name`   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `deleted`  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '删除标识：0-未删除，1-已删除',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统角色表';

-- ---------------------------------------------------------------------
-- 系统权限表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `permission_code` VARCHAR(128) NOT NULL COMMENT '权限编码，格式 模块:资源:动作',
    `permission_name` VARCHAR(128) NOT NULL COMMENT '权限名称',
    `parent_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父权限 ID，0 表示根',
    `sort`            INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '删除标识：0-未删除，1-已删除',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_permission_code` (`permission_code`),
    KEY `idx_sys_permission_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统权限表';

-- ---------------------------------------------------------------------
-- 用户-角色关联表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id`      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_role` (`user_id`, `role_id`),
    KEY `idx_sys_user_role_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户-角色关联表';

-- ---------------------------------------------------------------------
-- 角色-权限关联表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT UNSIGNED NOT NULL COMMENT '角色 ID',
    `permission_id` BIGINT UNSIGNED NOT NULL COMMENT '权限 ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_permission` (`role_id`, `permission_id`),
    KEY `idx_sys_role_permission_permission_id` (`permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色-权限关联表';

-- 操作日志表 operation_log 由项目方按《04-数据库设计.md》建库时统一创建，
-- 本工程不再重复创建，操作日志 AOP 直接写入该表。
