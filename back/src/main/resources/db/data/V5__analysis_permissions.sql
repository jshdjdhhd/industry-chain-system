-- =====================================================================
-- 产业链管理系统 - 健康分析报告权限种子数据
-- 版本: V5
-- 说明:
--   1. 新增分析诊断与报告相关权限
--   2. 为 admin 角色分配全部 analysis 权限
-- =====================================================================

INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `parent_id`, `sort`)
SELECT seed.`permission_code`, seed.`permission_name`, seed.`parent_id`, seed.`sort`
FROM (
    SELECT 'analysis:diagnosis:query'  AS `permission_code`, '诊断树查询'  AS `permission_name`, 0 AS `parent_id`, 40 AS `sort`
    UNION ALL SELECT 'analysis:report:create',   '创建报告',    0, 41
    UNION ALL SELECT 'analysis:report:query',     '报告查询',    0, 42
    UNION ALL SELECT 'analysis:report:retry',     '重试报告',    0, 43
    UNION ALL SELECT 'analysis:report:download',  '下载报告',    0, 44
) seed
LEFT JOIN `sys_permission` existing
    ON existing.`permission_code` = seed.`permission_code`
WHERE existing.`id` IS NULL;

-- 为 admin 角色分配权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, permission_rows.`id`
FROM `sys_permission` permission_rows
LEFT JOIN `sys_role_permission` existing
    ON existing.`role_id` = 1
   AND existing.`permission_id` = permission_rows.`id`
WHERE permission_rows.`permission_code` LIKE 'analysis:%'
  AND existing.`permission_id` IS NULL;
