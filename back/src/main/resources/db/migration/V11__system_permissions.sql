-- 系统管理模块权限码 + admin 角色自动授权
INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `parent_id`, `sort`)
SELECT seed.`code`, seed.`name`, 0, seed.`sort`
FROM (
    SELECT 'system:user:query'        AS `code`, '用户管理-查询'   AS `name`, 37 AS `sort`
    UNION ALL SELECT 'system:user:create',       '用户管理-新增',   38
    UNION ALL SELECT 'system:user:update',       '用户管理-修改',   39
    UNION ALL SELECT 'system:user:delete',       '用户管理-删除',   40
    UNION ALL SELECT 'system:user:reset-pwd',    '用户管理-重置密码', 41
    UNION ALL SELECT 'system:role:query',        '角色管理-查询',   42
    UNION ALL SELECT 'system:role:create',       '角色管理-新增',   43
    UNION ALL SELECT 'system:role:update',       '角色管理-修改',   44
    UNION ALL SELECT 'system:role:delete',       '角色管理-删除',   45
    UNION ALL SELECT 'system:permission:query',  '权限管理-查询',   46
    UNION ALL SELECT 'system:log:query',         '操作日志-查询',   47
) seed
LEFT JOIN `sys_permission` existing ON existing.`permission_code` = seed.`code`
WHERE existing.`id` IS NULL;

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id`
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp ON rp.`role_id` = 1 AND rp.`permission_id` = p.`id`
WHERE p.`permission_code` LIKE 'system:%'
  AND rp.`id` IS NULL;
