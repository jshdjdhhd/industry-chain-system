-- =====================================================================
-- 产业链管理系统 - 演示数据（业务核心）
-- 版本: V2
-- 说明: 插入演示用产业链、节点、企业、匹配任务及结果数据，供本地联调。
--       产业链: 氢能源与新能源汽车零部件
--       包含: 3 个一级节点（上游/中游/下游）+ 6 个二级节点
--             10 家企业（至少 5 家有清晰关键词）
--             标签数据、匹配任务、推荐结果、企业-节点关系
-- =====================================================================

-- ---------------------------------------------------------------------
-- 产业链（chain）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chain` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `chain_name`  VARCHAR(128) NOT NULL COMMENT '产业链名称',
    `description` TEXT         NULL     COMMENT '产业链描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '产业链表';

INSERT INTO `chain` (`id`, `chain_name`, `description`) VALUES
    (1, '氢能源与新能源汽车零部件产业链',
     '涵盖氢气制取、燃料电池、动力电池、电驱系统、整车制造、充换电设施等环节的完整产业链'),
    (2, '半导体产业链', '涵盖设计、制造、封测、设备等环节的完整产业链');

-- ---------------------------------------------------------------------
-- 产业链节点（chain_node）- 3 个一级节点 + 6 个二级节点
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chain_node` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `chain_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属产业链ID',
    `node_name`   VARCHAR(128) NOT NULL COMMENT '节点名称',
    `node_level`  INT NOT NULL DEFAULT 1 COMMENT '节点层级',
    `description` TEXT         NULL     COMMENT '节点描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_chain_node_chain_id` (`chain_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '产业链节点表';

INSERT INTO `chain_node` (`id`, `chain_id`, `node_name`, `node_level`, `description`) VALUES
    -- ---- 上游（level=1）----
    (101, 1, '氢气制取',       1, '电解水制氢、工业副产氢等氢气生产环节'),
    (102, 1, '氢气储运',       1, '氢气压缩、液化、管道运输及加氢站建设'),
    -- ---- 中游（level=2）----
    (201, 1, '燃料电池',       2, '燃料电池电堆及核心零部件制造'),
    (202, 1, '动力电池',       2, '锂离子动力电池电芯及模组制造'),
    (203, 1, '电驱系统',       2, '驱动电机及电控系统制造'),
    -- ---- 下游（level=3）----
    (301, 1, '新能源整车',     3, '氢燃料电池汽车及纯电动汽车整车制造'),
    (302, 1, '充换电设施',     3, '充电桩、换电站建设与运营');

-- ---------------------------------------------------------------------
-- 企业（enterprise）- 10 家企业，至少 5 家有清晰关键词
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `enterprise_name` VARCHAR(200) NOT NULL COMMENT '企业名称',
    `unified_code`    VARCHAR(32)  NULL     COMMENT '统一社会信用代码',
    `region`          VARCHAR(64)  NULL     COMMENT '所在地区',
    `industry`        VARCHAR(64)  NULL     COMMENT '所属行业',
    `business_scope`  TEXT         NULL     COMMENT '经营范围',
    `main_products`   TEXT         NULL     COMMENT '主营产品',
    `core_tech`       TEXT         NULL     COMMENT '核心技术',
    `keywords`        VARCHAR(500) NULL     COMMENT '核心关键词（逗号分隔）',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_unified_code` (`unified_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '企业表';

-- 上游企业
INSERT INTO `enterprise` (`id`, `enterprise_name`, `unified_code`, `region`, `industry`, `keywords`, `status`) VALUES
    (1,  '隆基氢能科技有限公司',     '91610100MA6UXXXX01', '陕西省', '氢气制取', '电解水制氢,绿氢,碱性电解槽,PEM电解', 'ACTIVE'),
    (2,  '中石化氢能有限公司',       '91110000MA0XXXXX02', '北京市', '氢气储运', '加氢站,高压储氢,液氢,氢气运输',      'ACTIVE'),
    (3,  '北京亿华通科技股份有限公司', '91110108MA0XXXXX03', '北京市', '燃料电池', '燃料电池电堆,氢燃料电池,质子交换膜',   'ACTIVE');

-- 中游企业
INSERT INTO `enterprise` (`id`, `enterprise_name`, `unified_code`, `region`, `industry`, `keywords`, `status`) VALUES
    (4,  '宁德时代新能源科技股份有限公司', '91350900MA3359X93F', '福建省', '动力电池', '动力电池,电芯,模组,磷酸铁锂,三元锂', 'ACTIVE'),
    (5,  '精进电动科技股份有限公司',       '91110108MA0XXXXX05', '北京市', '电驱系统', '驱动电机,电控,新能源汽车电驱',         'ACTIVE'),
    (6,  '大洋电机股份有限公司',           '91442000MA5XXXXX06', '广东省', '电驱系统', '驱动电机,新能源动力总成',               'ACTIVE');

-- 下游企业
INSERT INTO `enterprise` (`id`, `enterprise_name`, `unified_code`, `region`, `industry`, `keywords`, `status`) VALUES
    (7,  '比亚迪股份有限公司',             '914403001925037716', '广东省', '整车制造', '新能源汽车,纯电动,插电混动,整车制造', 'ACTIVE'),
    (8,  '北京汽车股份有限公司',           '91110000MA0XXXXX08', '北京市', '整车制造', '北汽新能源,电动汽车',                   'ACTIVE'),
    (9,  '特锐德电气股份有限公司',         '913702007255724872', '山东省', '充换电设施', '充电桩,充电站,智能充电',               'ACTIVE'),
    (10, '蔚来汽车科技有限公司',           '91310000MA1XXXXX10', '上海市', '整车制造', '换电站,新能源汽车,智能驾驶',           'ACTIVE');

-- ---------------------------------------------------------------------
-- 标签数据
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `enterprise_tag` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `tag_name`     VARCHAR(64)  NOT NULL COMMENT '标签名称',
    `tag_value`    VARCHAR(128) NULL     COMMENT '标签值',
    `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_enterprise_tag_eid` (`enterprise_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '企业标签表';

INSERT INTO `enterprise_tag` (`enterprise_id`, `tag_name`, `tag_value`) VALUES
    (1,  '产业链定位', '上游-氢气制取'),
    (1,  '企业规模',   '大型'),
    (1,  '上市状态',   '上市'),
    (2,  '产业链定位', '上游-氢气储运'),
    (2,  '企业规模',   '大型央企'),
    (2,  '上市状态',   '上市'),
    (3,  '产业链定位', '中游-燃料电池'),
    (3,  '企业规模',   '中型'),
    (3,  '上市状态',   '上市'),
    (4,  '产业链定位', '中游-动力电池'),
    (4,  '企业规模',   '大型'),
    (4,  '上市状态',   '上市'),
    (5,  '产业链定位', '中游-电驱系统'),
    (5,  '企业规模',   '中型'),
    (5,  '上市状态',   '上市'),
    (6,  '产业链定位', '中游-电驱系统'),
    (6,  '企业规模',   '中型'),
    (6,  '上市状态',   '上市'),
    (7,  '产业链定位', '下游-新能源整车'),
    (7,  '企业规模',   '大型'),
    (7,  '上市状态',   '上市'),
    (8,  '产业链定位', '下游-新能源整车'),
    (8,  '企业规模',   '大型'),
    (8,  '上市状态',   '上市'),
    (9,  '产业链定位', '下游-充换电设施'),
    (9,  '企业规模',   '大型'),
    (9,  '上市状态',   '上市'),
    (10, '产业链定位', '下游-新能源整车'),
    (10, '企业规模',   '大型'),
    (10, '上市状态',   '上市'),
    (3,  '技术路线',   '氢燃料电池'),
    (4,  '技术路线',   '锂离子电池'),
    (7,  '技术路线',   '纯电动+插电混动'),
    (10, '技术路线',   '纯电动');

CREATE TABLE IF NOT EXISTS `tag` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `tag_name`    VARCHAR(64) NOT NULL COMMENT '标签名称',
    `tag_type`    VARCHAR(32) NOT NULL DEFAULT 'CUSTOM' COMMENT '标签类型',
    `color`       VARCHAR(16) NULL COMMENT '标签颜色',
    `description` VARCHAR(255) NULL COMMENT '标签描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tag_type` (`tag_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签表';

CREATE TABLE IF NOT EXISTS `enterprise_tag_rel` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '企业ID',
    `tag_id`        BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_enterprise_tag_rel_eid` (`enterprise_id`),
    KEY `idx_enterprise_tag_rel_tag_id` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '企业标签关系表';

INSERT INTO `tag` (`id`, `tag_name`, `tag_type`, `color`, `description`) VALUES
    (1, '上游-氢气制取', 'CHAIN_POSITION', '#002FA7', '产业链定位'),
    (2, '上游-氢气储运', 'CHAIN_POSITION', '#002FA7', '产业链定位'),
    (3, '中游-燃料电池', 'CHAIN_POSITION', '#002FA7', '产业链定位'),
    (4, '中游-动力电池', 'CHAIN_POSITION', '#002FA7', '产业链定位'),
    (5, '中游-电驱系统', 'CHAIN_POSITION', '#002FA7', '产业链定位'),
    (6, '下游-新能源整车', 'CHAIN_POSITION', '#002FA7', '产业链定位'),
    (7, '下游-充换电设施', 'CHAIN_POSITION', '#002FA7', '产业链定位'),
    (8, '氢燃料电池', 'TECH_ROUTE', '#00A36C', '技术路线'),
    (9, '锂离子电池', 'TECH_ROUTE', '#00A36C', '技术路线'),
    (10, '纯电动+插电混动', 'TECH_ROUTE', '#00A36C', '技术路线'),
    (11, '纯电动', 'TECH_ROUTE', '#00A36C', '技术路线');

INSERT INTO `enterprise_tag_rel` (`enterprise_id`, `tag_id`) VALUES
    (1, 1),
    (2, 2),
    (3, 3), (3, 8),
    (4, 4), (4, 9),
    (5, 5),
    (6, 5),
    (7, 6), (7, 10),
    (8, 6),
    (9, 7),
    (10, 6), (10, 11);

-- ---------------------------------------------------------------------
-- 演示匹配任务（供前端直接展示，无需等待脚本创建）
-- ---------------------------------------------------------------------
INSERT INTO `matching_task` (`id`, `enterprise_id`, `chain_id`, `input_snapshot`, `model_name`, `status`, `created_by`) VALUES
    (1, 3,  1, '{"keywords":["燃料电池电堆","氢燃料电池","质子交换膜"]}', 'RULE_BASED', 'COMPLETED', 1),
    (2, 4,  1, '{"keywords":["动力电池","电芯","模组","磷酸铁锂"]}',     'RULE_BASED', 'COMPLETED', 1),
    (3, 7,  1, '{"keywords":["新能源汽车","纯电动","插电混动"]}',         'RULE_BASED', 'COMPLETED', 1),
    (4, 10, 1, '{"keywords":["换电站","新能源汽车","智能驾驶"]}',         'RULE_BASED', 'COMPLETED', 1);

-- ---------------------------------------------------------------------
-- 演示匹配结果（每个任务 2-3 条推荐，含不同决策状态）
-- ---------------------------------------------------------------------
INSERT INTO `matching_result` (`id`, `task_id`, `node_id`, `confidence`, `reason`, `evidence`, `rank_no`, `decision`) VALUES
    -- 任务1：亿华通 → 燃料电池
    (1,  1, 201, 0.9600, '企业核心业务为燃料电池电堆制造，与燃料电池节点高度匹配',
     '{"keyword_match":["燃料电池电堆","质子交换膜"],"industry_match":true}', 1, 'ACCEPTED'),
    (2,  1, 101, 0.3000, '企业涉及氢气使用，但非氢气生产环节',
     '{"keyword_match":["氢燃料电池"],"industry_partial":true}',              2, 'PENDING'),
    -- 任务2：宁德时代 → 动力电池
    (3,  2, 202, 0.9500, '企业主营业务为动力电池电芯及模组制造，与动力电池节点高度匹配',
     '{"keyword_match":["动力电池","电芯","磷酸铁锂"],"industry_match":true}', 1, 'ACCEPTED'),
    (4,  2, 203, 0.1800, '企业无电驱系统业务，仅在电池模组集成中有所涉及',
     '{}',                                                                     3, 'REJECTED'),
    -- 任务3：比亚迪 → 新能源整车
    (5,  3, 301, 0.9300, '企业为国内领先的新能源汽车整车制造商，与整车制造节点高度匹配',
     '{"keyword_match":["新能源汽车","纯电动","整车制造"],"industry_match":true}', 1, 'PENDING'),
    (6,  3, 202, 0.2500, '企业旗下有电池业务，但非主体业务',
     '{"keyword_match":["磷酸铁锂"],"industry_partial":true}',                      2, 'PENDING'),
    -- 任务4：蔚来 → 新能源整车（换电站特色）
    (7,  4, 301, 0.8200, '企业为新能源汽车整车制造商，与整车节点匹配',
     '{"keyword_match":["新能源汽车","智能驾驶"],"industry_match":true}',       1, 'PENDING'),
    (8,  4, 302, 0.7800, '企业自建换电站网络，与充换电设施节点高度匹配',
     '{"keyword_match":["换电站"],"industry_match":true}',                       2, 'PENDING');

-- ---------------------------------------------------------------------
-- 演示企业-节点关系（正式挂链，供前端节点企业列表展示）
-- ---------------------------------------------------------------------
-- 注意: enterprise 3→201 和 enterprise 7→301 的关系由联调脚本
-- 的 accept/manual-confirm 操作动态创建，此处不预置，避免主键冲突。
INSERT INTO `enterprise_node_rel` (`id`, `enterprise_id`, `node_id`, `source`, `confidence`, `reason`, `confirmed_by`) VALUES
    (1, 4,  202, 'AI',     0.9500, 'AI推荐：宁德时代匹配动力电池节点', 1),
    (2, 1,  101, 'AI',     0.9000, 'AI推荐：隆基氢能匹配氢气制取节点', 1),
    (3, 9,  302, 'IMPORT', NULL,   '批量导入：特锐德匹配充换电设施节点', NULL);
