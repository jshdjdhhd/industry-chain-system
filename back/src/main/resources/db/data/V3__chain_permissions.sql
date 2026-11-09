-- =====================================================================
-- 产业链管理系统 - 权限基线收口与兼容迁移
-- 版本: V3
-- 说明:
--   1. 将历史权限码收口到统一权限命名
--   2. 补齐缺失的 canonical 权限
--   3. 保障 admin 角色拥有全部 canonical 权限
-- =====================================================================

UPDATE `sys_permission`
SET `permission_code` = 'enterprise:enterprise:query',
    `permission_name` = '企业-查询'
WHERE `permission_code` = 'enterprise:query';

UPDATE `sys_permission`
SET `permission_code` = 'enterprise:tag:query',
    `permission_name` = '标签-查询'
WHERE `permission_code` = 'tag:tag:query';

UPDATE `sys_permission`
SET `permission_code` = 'enterprise:tag:update',
    `permission_name` = '标签-修改'
WHERE `permission_code` = 'tag:tag:update';

UPDATE `sys_permission`
SET `permission_code` = 'chain:node:link-enterprise',
    `permission_name` = '链节点-挂链'
WHERE `permission_code` = 'chain:node:bind';

UPDATE `sys_permission`
SET `permission_code` = 'chain:node:batch-link-enterprise',
    `permission_name` = '链节点-批量挂链'
WHERE `permission_code` = 'chain:node:batch-bind';

UPDATE `sys_permission`
SET `permission_code` = 'chain:node:unlink-enterprise',
    `permission_name` = '链节点-解绑'
WHERE `permission_code` = 'chain:node:unbind';

UPDATE `sys_permission`
SET `permission_code` = 'matching:manual-confirm:create',
    `permission_name` = '人工确认挂链'
WHERE `permission_code` = 'matching:manual:confirm';

INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `parent_id`, `sort`)
SELECT seed.`permission_code`, seed.`permission_name`, seed.`parent_id`, seed.`sort`
FROM (
    SELECT 'enterprise:enterprise:query'          AS `permission_code`, '企业-查询'       AS `permission_name`, 0 AS `parent_id`, 1  AS `sort`
    UNION ALL SELECT 'enterprise:enterprise:create',         '企业-新增',       0, 2
    UNION ALL SELECT 'enterprise:enterprise:update',         '企业-修改',       0, 3
    UNION ALL SELECT 'enterprise:enterprise:delete',         '企业-删除',       0, 4
    UNION ALL SELECT 'enterprise:enterprise:inbound',        '企业-入库',       0, 5
    UNION ALL SELECT 'enterprise:enterprise:outbound',       '企业-出库',       0, 6
    UNION ALL SELECT 'enterprise:enterprise:batch-outbound', '企业-批量出库',   0, 7
    UNION ALL SELECT 'enterprise:enterprise:import',         '企业-导入',       0, 8
    UNION ALL SELECT 'enterprise:tag:bind',                  '企业标签-绑定',   0, 9
    UNION ALL SELECT 'enterprise:tag:unbind',                '企业标签-解绑',   0, 10
    UNION ALL SELECT 'enterprise:tag:query',                 '标签-查询',       0, 11
    UNION ALL SELECT 'enterprise:tag:create',                '标签-新增',       0, 12
    UNION ALL SELECT 'enterprise:tag:update',                '标签-修改',       0, 13
    UNION ALL SELECT 'enterprise:tag:delete',                '标签-删除',       0, 14
    UNION ALL SELECT 'chain:chain:query',                    '产业链-查询',     0, 15
    UNION ALL SELECT 'chain:chain:create',                   '产业链-创建',     0, 16
    UNION ALL SELECT 'chain:chain:update',                   '产业链-修改',     0, 17
    UNION ALL SELECT 'chain:chain:delete',                   '产业链-删除',     0, 18
    UNION ALL SELECT 'chain:node:query',                     '链节点-查询',     0, 19
    UNION ALL SELECT 'chain:node:create',                    '链节点-创建',     0, 20
    UNION ALL SELECT 'chain:node:update',                    '链节点-修改',     0, 21
    UNION ALL SELECT 'chain:node:move',                      '链节点-移动',     0, 22
    UNION ALL SELECT 'chain:node:sort',                      '链节点-排序',     0, 23
    UNION ALL SELECT 'chain:node:delete',                    '链节点-删除',     0, 24
    UNION ALL SELECT 'chain:node:link-enterprise',           '链节点-挂链',     0, 25
    UNION ALL SELECT 'chain:node:batch-link-enterprise',     '链节点-批量挂链', 0, 26
    UNION ALL SELECT 'chain:node:unlink-enterprise',         '链节点-解绑',     0, 27
    UNION ALL SELECT 'matching:task:query',                  '匹配任务-查询',   0, 28
    UNION ALL SELECT 'matching:task:create',                 '匹配任务-创建',   0, 29
    UNION ALL SELECT 'matching:task:execute',                '匹配任务-执行',   0, 30
    UNION ALL SELECT 'matching:task:cancel',                 '匹配任务-取消',   0, 31
    UNION ALL SELECT 'matching:task:retry',                  '匹配任务-重试',   0, 32
    UNION ALL SELECT 'matching:result:accept',               '匹配结果-采纳',   0, 33
    UNION ALL SELECT 'matching:result:reject',               '匹配结果-拒绝',   0, 34
    UNION ALL SELECT 'matching:manual-confirm:create',       '人工确认挂链',    0, 35
    UNION ALL SELECT 'matching:evaluation:run',              '匹配评估-执行',   0, 36
) seed
LEFT JOIN `sys_permission` existing
    ON existing.`permission_code` = seed.`permission_code`
WHERE existing.`id` IS NULL;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, permission_rows.`id`
FROM `sys_permission` permission_rows
LEFT JOIN `sys_role_permission` existing
    ON existing.`role_id` = 1
   AND existing.`permission_id` = permission_rows.`id`
WHERE permission_rows.`permission_code` IN (
    'enterprise:enterprise:query',
    'enterprise:enterprise:create',
    'enterprise:enterprise:update',
    'enterprise:enterprise:delete',
    'enterprise:enterprise:inbound',
    'enterprise:enterprise:outbound',
    'enterprise:enterprise:batch-outbound',
    'enterprise:enterprise:import',
    'enterprise:tag:bind',
    'enterprise:tag:unbind',
    'enterprise:tag:query',
    'enterprise:tag:create',
    'enterprise:tag:update',
    'enterprise:tag:delete',
    'chain:chain:query',
    'chain:chain:create',
    'chain:chain:update',
    'chain:chain:delete',
    'chain:node:query',
    'chain:node:create',
    'chain:node:update',
    'chain:node:move',
    'chain:node:sort',
    'chain:node:delete',
    'chain:node:link-enterprise',
    'chain:node:batch-link-enterprise',
    'chain:node:unlink-enterprise',
    'matching:task:query',
    'matching:task:create',
    'matching:task:execute',
    'matching:task:cancel',
    'matching:task:retry',
    'matching:result:accept',
    'matching:result:reject',
    'matching:manual-confirm:create',
    'matching:evaluation:run'
)
AND existing.`permission_id` IS NULL;
