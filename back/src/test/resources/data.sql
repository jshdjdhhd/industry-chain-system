-- 测试期演示数据：admin/admin123。幂等（先清理再插入），避免跨上下文复用 H2 时主键冲突。
DELETE FROM sys_role_permission;
DELETE FROM sys_user_role;
DELETE FROM sys_permission;
DELETE FROM sys_role;
DELETE FROM sys_user;

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

-- =====================================================================
-- 业务核心演示数据（H2 兼容）
-- =====================================================================
DELETE FROM enterprise_node_rel;
DELETE FROM matching_recommendation_cache;
DELETE FROM matching_result;
DELETE FROM matching_task;
DELETE FROM enterprise_tag_rel;
DELETE FROM tag;
DELETE FROM chain_node;
DELETE FROM chain;
DELETE FROM enterprise;

INSERT INTO enterprise (id, enterprise_name, unified_code, region, industry, business_scope, main_products, core_tech, keywords, status) VALUES
    (1, '天齐锂业股份有限公司', '91510100201912345A', '四川省', '锂矿开采', '锂矿开采', '锂矿,锂辉石,碳酸锂', NULL, NULL, 'ACTIVE'),
    (2, '宁德时代新能源科技股份有限公司', '91350900MA3359X93F', '福建省', '动力电池', '动力电池', '动力电池,电芯,模组', '锂离子电池', NULL, 'ACTIVE'),
    (3, '比亚迪股份有限公司', '914403001925037716', '广东省', '整车制造', '整车制造', '新能源汽车,纯电动,整车制造', NULL, NULL, 'ACTIVE'),
    (5, '华为海思半导体有限公司', '91440300792598765B', '广东省', '芯片设计', '芯片设计', '芯片设计,ASIC,SoC', NULL, NULL, 'ACTIVE');

INSERT INTO tag (id, tag_name, tag_type, color, description, is_deleted) VALUES
    (1, '中游-动力电池', 'CHAIN_POSITION', '#002FA7', '产业链定位', 0),
    (2, '锂离子电池', 'TECH_ROUTE', '#00A36C', '技术路线', 0);

INSERT INTO enterprise_tag_rel (enterprise_id, tag_id) VALUES
    (2, 1),
    (2, 2);

INSERT INTO chain (id, chain_name, description) VALUES
    (1, '新能源汽车产业链', '覆盖动力电池、电驱系统和整车制造'),
    (2, '半导体产业链', '覆盖设计、制造和封测');

INSERT INTO chain_node
    (id, chain_id, parent_id, node_name, keywords, node_level,
     description, node_path, sort_order, stage_type)
VALUES
    (100, 1, NULL, '中游', '中游,核心制造', 1,
     '新能源汽车核心制造环节', '/100/', 1, 'MIDSTREAM'),
    (101, 1, 100, '锂矿开采', '锂矿,锂辉石,碳酸锂', 2,
     '锂资源供给', '/100/101/', 1, NULL),
    (102, 1, 100, '电池制造', '电池,锂电池,动力电池,电芯,模组', 2,
     '电池生产制造环节', '/100/102/', 2, NULL),
    (103, 1, NULL, '整车制造', '新能源汽车,纯电动,整车制造', 1,
     '新能源汽车整车制造', '/103/', 3, NULL),
    (201, 2, NULL, '芯片设计', '芯片设计,ASIC,SoC', 1,
     '集成电路设计环节', '/201/', 1, NULL),
    (202, 2, NULL, '晶圆制造', '晶圆代工,半导体制造', 1,
     '晶圆制造环节', '/202/', 2, NULL);

INSERT INTO matching_task (id, enterprise_id, chain_id, input_snapshot, model_name, status, created_by) VALUES
    (1, 2, 1, '{"keywords":["动力电池","电芯","模组"]}', 'RULE_BASED', 'COMPLETED', 1),
    (2, 5, 2, '{"keywords":["芯片设计","ASIC","SoC"]}', 'RULE_BASED', 'COMPLETED', 1);

INSERT INTO matching_result (id, task_id, node_id, confidence, reason, evidence, rank_no, decision) VALUES
    (1, 1, 102, 0.9500, '企业主营业务为动力电池电芯及模组制造，与电池制造节点高度匹配', '{"keyword_match":["动力电池","电芯"]}', 1, 'ACCEPTED'),
    (2, 1, 101, 0.3200, '企业涉及锂矿上游供应，但非核心业务', '{}', 2, 'PENDING'),
    (3, 2, 201, 0.8800, '企业核心业务为芯片设计，与设计节点高度匹配', '{"keyword_match":["芯片设计"]}', 1, 'ACCEPTED'),
    (4, 2, 202, 0.1500, '企业无晶圆制造业务', '{}', 3, 'REJECTED');

INSERT INTO enterprise_node_rel (id, enterprise_id, node_id, source, confidence, reason, confirmed_by) VALUES
    (1, 2, 102, 'AI', 0.9500, 'AI推荐：宁德时代匹配电池制造节点', 1),
    (2, 3, 103, 'MANUAL', NULL, '人工确认：比亚迪匹配整车制造节点', 1),
    (3, 1, 101, 'AI', 0.9000, 'AI推荐：天齐锂业匹配锂矿开采节点', 1),
    (4, 5, 201, 'IMPORT', NULL, '批量导入：华为海思匹配芯片设计节点', NULL);
