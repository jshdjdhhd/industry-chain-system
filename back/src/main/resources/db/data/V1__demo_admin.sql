-- =====================================================================
-- 产业链管理系统 - 演示鉴权基线数据
-- 版本: V1
-- 说明:
--   1. 建立 admin 角色与 admin 用户
--   2. 收口企业/标签/产业链/匹配权限到统一权限码
--   3. admin 默认拥有全部权限
--   用户名: admin
--   明文密码: admin123
-- =====================================================================

INSERT INTO `sys_role` (`id`, `role_code`, `role_name`)
VALUES (1, 'admin', '超级管理员');

INSERT INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `parent_id`, `sort`)
VALUES
    (1,  'enterprise:enterprise:query',          '企业-查询',         0, 1),
    (2,  'enterprise:enterprise:create',         '企业-新增',         0, 2),
    (3,  'enterprise:enterprise:update',         '企业-修改',         0, 3),
    (4,  'enterprise:enterprise:delete',         '企业-删除',         0, 4),
    (5,  'enterprise:enterprise:inbound',        '企业-入库',         0, 5),
    (6,  'enterprise:enterprise:outbound',       '企业-出库',         0, 6),
    (7,  'enterprise:enterprise:batch-outbound', '企业-批量出库',     0, 7),
    (8,  'enterprise:enterprise:import',         '企业-导入',         0, 8),
    (9,  'enterprise:tag:bind',                  '企业标签-绑定',     0, 9),
    (10, 'enterprise:tag:unbind',                '企业标签-解绑',     0, 10),
    (11, 'enterprise:tag:query',                 '标签-查询',         0, 11),
    (12, 'enterprise:tag:create',                '标签-新增',         0, 12),
    (13, 'enterprise:tag:update',                '标签-修改',         0, 13),
    (14, 'enterprise:tag:delete',                '标签-删除',         0, 14),
    (15, 'chain:chain:query',                    '产业链-查询',       0, 15),
    (16, 'chain:chain:create',                   '产业链-创建',       0, 16),
    (17, 'chain:chain:update',                   '产业链-修改',       0, 17),
    (18, 'chain:chain:delete',                   '产业链-删除',       0, 18),
    (19, 'chain:node:query',                     '链节点-查询',       0, 19),
    (20, 'chain:node:create',                    '链节点-创建',       0, 20),
    (21, 'chain:node:update',                    '链节点-修改',       0, 21),
    (22, 'chain:node:move',                      '链节点-移动',       0, 22),
    (23, 'chain:node:sort',                      '链节点-排序',       0, 23),
    (24, 'chain:node:delete',                    '链节点-删除',       0, 24),
    (25, 'chain:node:link-enterprise',           '链节点-挂链',       0, 25),
    (26, 'chain:node:batch-link-enterprise',     '链节点-批量挂链',   0, 26),
    (27, 'chain:node:unlink-enterprise',         '链节点-解绑',       0, 27),
    (28, 'matching:task:query',                  '匹配任务-查询',     0, 28),
    (29, 'matching:task:create',                 '匹配任务-创建',     0, 29),
    (30, 'matching:task:execute',                '匹配任务-执行',     0, 30),
    (31, 'matching:task:cancel',                 '匹配任务-取消',     0, 31),
    (32, 'matching:task:retry',                  '匹配任务-重试',     0, 32),
    (33, 'matching:result:accept',               '匹配结果-采纳',     0, 33),
    (34, 'matching:result:reject',               '匹配结果-拒绝',     0, 34),
    (35, 'matching:manual-confirm:create',       '人工确认挂链',      0, 35),
    (36, 'matching:evaluation:run',              '匹配评估-执行',     0, 36);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`;

INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `status`)
VALUES (
    1,
    'admin',
    '{bcrypt}$2a$10$F/gB3i9y.Kp.Tt0vgaShrOYGR8oYtByzXJS5uTPEAHE2lPND3oHtS',
    '系统管理员',
    1
);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
VALUES (1, 1);
