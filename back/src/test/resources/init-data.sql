-- =====================================================================
-- 联调验收演示数据初始化脚本
-- 适用环境：H2 内存库（MySQL 兼容模式）或 MySQL
-- 说明：覆盖新能源汽车产业链 + 半导体产业链，含 12 家企业、
--       12 个节点、已有挂链关系及推荐任务数据。
--       幂等执行（先删后插），可重复运行。
-- =====================================================================

-- ------------------------------------------------------------------
-- 0. 清理已有数据（按外键依赖顺序）
-- ------------------------------------------------------------------
DELETE FROM operation_log;
DELETE FROM enterprise_node_rel;
DELETE FROM matching_result;
DELETE FROM matching_task;
DELETE FROM chain_node;
DELETE FROM chain;
DELETE FROM sys_role_permission;
DELETE FROM sys_user_role;
DELETE FROM sys_permission;
DELETE FROM sys_role;
DELETE FROM sys_user;

-- ------------------------------------------------------------------
-- 1. 权限与用户（admin / admin123）
-- ------------------------------------------------------------------
INSERT INTO sys_role (id, role_code, role_name) VALUES
    (1, 'admin', '超级管理员'),
    (2, 'enterprise_viewer', '企业只读用户');

INSERT INTO sys_permission (id, permission_code, permission_name, parent_id, sort) VALUES
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

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (2, 1);

INSERT INTO sys_user (id, username, password, real_name, email, phone, status) VALUES
    (1, 'admin',  '{bcrypt}$2a$10$F/gB3i9y.Kp.Tt0vgaShrOYGR8oYtByzXJS5uTPEAHE2lPND3oHtS', '系统管理员',   'admin@example.com',  '13800000001', 1),
    (2, 'viewer', '{bcrypt}$2a$10$F/gB3i9y.Kp.Tt0vgaShrOYGR8oYtByzXJS5uTPEAHE2lPND3oHtS', '企业只读用户', 'viewer@example.com', '13800000002', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
    (1, 1),
    (2, 2);

-- ------------------------------------------------------------------
-- 2. 产业链、固定阶段与细分节点
-- ------------------------------------------------------------------
INSERT INTO chain (id, chain_name, description) VALUES
    (1, '新能源汽车产业链', '覆盖上游材料、中游制造和下游应用'),
    (2, '半导体产业链', '覆盖设计、制造和封测');

INSERT INTO chain_node
    (id, chain_id, parent_id, node_name, keywords, node_level,
     description, node_path, sort_order, stage_type)
VALUES
    (1001, 1, NULL, '上游', '上游,供应端,原材料', 1,
     '产业链上游阶段', '/1001/', 1, 'UPSTREAM'),
    (1002, 1, NULL, '中游', '中游,制造端,核心环节', 1,
     '产业链中游阶段', '/1002/', 2, 'MIDSTREAM'),
    (1003, 1, NULL, '下游', '下游,应用端,市场端', 1,
     '产业链下游阶段', '/1003/', 3, 'DOWNSTREAM'),
    (2001, 2, NULL, '上游', '上游,供应端,原材料', 1,
     '产业链上游阶段', '/2001/', 1, 'UPSTREAM'),
    (2002, 2, NULL, '中游', '中游,制造端,核心环节', 1,
     '产业链中游阶段', '/2002/', 2, 'MIDSTREAM'),
    (2003, 2, NULL, '下游', '下游,应用端,市场端', 1,
     '产业链下游阶段', '/2003/', 3, 'DOWNSTREAM'),
    (101, 1, 1001, '氢气制取', '电解水制氢,绿氢', 2,
     '氢气制取环节', '/1001/101/', 1, NULL),
    (102, 1, 1001, '氢气储运', '储氢,液氢,加氢站', 2,
     '氢气储运环节', '/1001/102/', 2, NULL),
    (202, 1, 1002, '动力电池', '动力电池,电芯,模组', 2,
     '动力电池制造', '/1002/202/', 1, NULL),
    (203, 1, 1002, '电驱系统', '驱动电机,电控', 2,
     '电驱系统制造', '/1002/203/', 2, NULL),
    (301, 1, 1003, '整车制造', '新能源汽车,整车制造', 2,
     '新能源汽车整车制造', '/1003/301/', 1, NULL),
    (302, 1, 1003, '充换电设施', '充电桩,换电站', 2,
     '充换电基础设施', '/1003/302/', 2, NULL),
    (501, 2, 2001, '芯片设计', '芯片设计,ASIC,SoC', 2,
     '集成电路设计', '/2001/501/', 1, NULL),
    (502, 2, 2002, '晶圆制造', '晶圆代工,半导体制造', 2,
     '晶圆制造', '/2002/502/', 1, NULL);

-- ------------------------------------------------------------------
-- 3. 推荐任务（matching_task）
--    产业链 ID：1 = 新能源汽车产业链，2 = 半导体产业链
-- ------------------------------------------------------------------
INSERT INTO matching_task (id, enterprise_id, chain_id, input_snapshot, model_name, status, created_by) VALUES
    -- 任务1：隆基氢能 → 新能源汽车产业链
    (1, 1, 1, '{"keywords":["电解水制氢","绿氢","碱性电解槽"]}', 'RULE_BASED', 'COMPLETED', 1),
    -- 任务2：中石化氢能 → 新能源汽车产业链
    (2, 2, 1, '{"keywords":["加氢站","高压储氢","液氢"]}', 'RULE_BASED', 'COMPLETED', 1),
    -- 任务3：宁德时代 → 新能源汽车产业链
    (3, 4, 1, '{"keywords":["动力电池","电芯","模组"]}', 'RULE_BASED', 'COMPLETED', 1),
    -- 任务4：华为海思 → 半导体产业链
    (4, 5, 2, '{"keywords":["芯片设计","ASIC","SoC"]}', 'RULE_BASED', 'COMPLETED', 1),
    -- 任务5：比亚迪 → 新能源汽车产业链
    (5, 7, 1, '{"keywords":["新能源汽车","纯电动","整车制造"]}', 'RULE_BASED', 'COMPLETED', 1),
    -- 任务6：蔚来汽车 → 新能源汽车产业链（用于测试拒绝流程）
    (6, 10, 1, '{"keywords":["换电站","新能源汽车","智能驾驶"]}', 'RULE_BASED', 'COMPLETED', 1);

-- ------------------------------------------------------------------
-- 4. 推荐结果（matching_result）
--    decision: PENDING / ACCEPTED / REJECTED
-- ------------------------------------------------------------------
INSERT INTO matching_result (id, task_id, node_id, confidence, reason, evidence, rank_no, decision) VALUES
    -- 任务1 结果：隆基氢能 → 氢气制取（上游）
    (1,  1, 101, 0.9500, '企业主营电解水制氢设备，与氢气制取节点高度匹配', '{"keyword_match":["电解水制氢","绿氢"]}', 1, 'PENDING'),
    (2,  1, 102, 0.4200, '企业涉及部分储氢技术研发，但非核心业务', '{}', 2, 'PENDING'),
    -- 任务2 结果：中石化氢能 → 氢气储运（上游）
    (3,  2, 102, 0.9200, '企业核心业务为加氢站建设运营和氢气储运', '{"keyword_match":["加氢站","高压储氢"]}', 1, 'PENDING'),
    (4,  2, 101, 0.2800, '企业有少量制氢业务，但非主营方向', '{}', 2, 'PENDING'),
    -- 任务3 结果：宁德时代 → 动力电池（中游）
    (5,  3, 202, 0.9500, '企业主营业务为动力电池电芯及模组制造', '{"keyword_match":["动力电池","电芯","模组"]}', 1, 'ACCEPTED'),
    (6,  3, 203, 0.1800, '企业无电驱系统业务', '{}', 3, 'REJECTED'),
    -- 任务4 结果：华为海思 → 芯片设计
    (7,  4, 501, 0.9100, '企业核心业务为芯片设计，与芯片设计节点高度匹配', '{"keyword_match":["芯片设计","ASIC","SoC"]}', 1, 'ACCEPTED'),
    (8,  4, 502, 0.2200, '企业无晶圆代工业务', '{}', 3, 'REJECTED'),
    -- 任务5 结果：比亚迪 → 整车制造（下游）
    (9,  5, 301, 0.9600, '企业核心业务为新能源汽车整车制造', '{"keyword_match":["新能源汽车","纯电动"]}', 1, 'ACCEPTED'),
    (10, 5, 202, 0.4500, '企业也生产动力电池，但更偏整车制造', '{}', 2, 'PENDING'),
    -- 任务6 结果：蔚来汽车 → 整车制造 + 充换电
    (11, 6, 301, 0.8800, '企业主营新能源汽车制造', '{"keyword_match":["新能源汽车"]}', 1, 'PENDING'),
    (12, 6, 302, 0.7200, '企业大力布局换电站网络', '{"keyword_match":["换电站"]}', 2, 'PENDING'),
    (13, 6, 202, 0.1500, '企业无动力电池制造业务', '{}', 3, 'PENDING');

-- ------------------------------------------------------------------
-- 5. 企业-节点挂链关系（enterprise_node_rel）
--    source: AI=推荐采纳 / MANUAL=人工确认 / IMPORT=批量导入
-- ------------------------------------------------------------------
INSERT INTO enterprise_node_rel (id, enterprise_id, node_id, source, confidence, reason, confirmed_by) VALUES
    -- 宁德时代 → 动力电池（推荐采纳）
    (1,  4,  202, 'AI',     0.9500, 'AI推荐：宁德时代匹配动力电池制造节点', 1),
    -- 华为海思 → 芯片设计（推荐采纳）
    (2,  5,  501, 'AI',     0.9100, 'AI推荐：华为海思匹配芯片设计节点', 1),
    -- 比亚迪 → 整车制造（推荐采纳）
    (3,  7,  301, 'AI',     0.9600, 'AI推荐：比亚迪匹配新能源整车制造节点', 1),
    -- 精进电动 → 电驱系统（人工确认）
    (4,  8,  203, 'MANUAL', NULL, '人工确认：精进电动主营驱动电机', 1),
    -- 特锐德 → 充换电设施（批量导入）
    (5,  9,  302, 'IMPORT', NULL, '批量导入：特锐德匹配充换电设施节点', NULL);
