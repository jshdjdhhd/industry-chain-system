-- =====================================================================
-- 选项查询性能索引
-- 版本: V20
-- 说明: 为智能挂链、AI 助手的下拉选项查询路径补复合索引。
--       chain_node 的路径与排序复合索引已由 V9 创建，本迁移不重复创建。
-- =====================================================================

-- 企业：ACTIVE 选项默认按创建时间倒序
ALTER TABLE `enterprise`
    ADD KEY `idx_enterprise_status_deleted_create_time`
        (`status`, `is_deleted`, `create_time`, `id`);

-- 企业：按名称前缀搜索
ALTER TABLE `enterprise`
    ADD KEY `idx_enterprise_status_deleted_name`
        (`status`, `is_deleted`, `enterprise_name`, `id`);

-- 关系：按节点加载有效企业
ALTER TABLE `enterprise_node_rel`
    ADD KEY `idx_rel_node_deleted_enterprise`
        (`node_id`, `is_deleted`, `enterprise_id`);

-- 关系：按企业加载有效挂链
ALTER TABLE `enterprise_node_rel`
    ADD KEY `idx_rel_enterprise_deleted_node`
        (`enterprise_id`, `is_deleted`, `node_id`);
