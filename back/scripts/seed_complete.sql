-- ============================================================
-- 完整数据填充脚本
-- ============================================================

-- 清理重复的半导体节点（保留ID较小的一组）
DELETE FROM chain_node WHERE id IN (333,334,335,336,337,338,339);
-- 清理错误创建的手机制造链
DELETE FROM chain_node WHERE chain_id=14;
DELETE FROM chain WHERE id=14;

-- ============================================================
-- 1. 新增产业链：光通信产业链、人工智能产业链
-- ============================================================
INSERT IGNORE INTO chain (chain_name, description) VALUES
('光通信产业链', '涵盖光芯片、光器件、光模块、光纤光缆、光通信设备等环节的完整产业链'),
('人工智能产业链', '涵盖AI芯片、基础软件、大模型、AI应用、智能硬件等环节的完整产业链');

-- 氢能源链节点（已存在，跳过）
-- 半导体链节点由 seed_data.sql 初始化。

-- 光通信、人工智能产业链阶段节点
INSERT INTO chain_node
    (chain_id, parent_id, node_name, keywords, node_level,
     node_path, sort_order, description, stage_type)
SELECT c.id, NULL, s.node_name, s.keywords, 1,
       '/', s.sort_order, s.description, s.stage_type
FROM chain c
JOIN (
    SELECT '上游' node_name, '上游,供应端,原材料' keywords,
           1 sort_order, '产业链上游阶段' description,
           'UPSTREAM' stage_type
    UNION ALL
    SELECT '中游', '中游,制造端,核心环节', 2,
           '产业链中游阶段', 'MIDSTREAM'
    UNION ALL
    SELECT '下游', '下游,应用端,市场端', 3,
           '产业链下游阶段', 'DOWNSTREAM'
) s
WHERE c.chain_name IN ('光通信产业链', '人工智能产业链')
  AND c.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM chain_node existing
      WHERE existing.chain_id = c.id
        AND existing.stage_type = s.stage_type
        AND existing.is_deleted = 0
  );

UPDATE chain_node stage
JOIN chain c ON c.id = stage.chain_id
SET stage.node_path = CONCAT('/', stage.id, '/'),
    stage.node_level = 1,
    stage.parent_id = NULL
WHERE c.chain_name IN ('光通信产业链', '人工智能产业链')
  AND stage.stage_type IS NOT NULL
  AND stage.is_deleted = 0;

-- 严格按已批准规格创建二级细分节点。
INSERT INTO chain_node
    (chain_id, parent_id, node_name, keywords, node_level,
     node_path, sort_order, description)
SELECT c.id, stage.id, seed.node_name, seed.keywords, 2,
       '/', seed.sort_order, seed.description
FROM chain c
JOIN (
    SELECT '光通信产业链' chain_name, 'UPSTREAM' stage_type,
           '光芯片' node_name,
           '光芯片,激光器芯片,探测器芯片,DFB,EML,VCSEL,硅光芯片' keywords,
           1 sort_order, '光芯片设计与制造' description
    UNION ALL
    SELECT '光通信产业链', 'UPSTREAM', '光器件',
           '光器件,光组件,光纤连接器,PLC,AWG,光分路器',
           2, '光无源和有源器件'
    UNION ALL
    SELECT '光通信产业链', 'MIDSTREAM', '光模块',
           '光模块,光收发模块,400G,800G,1.6T,数通光模块,电信光模块',
           1, '光模块研发与制造'
    UNION ALL
    SELECT '光通信产业链', 'MIDSTREAM', '光纤光缆',
           '光纤,光缆,光纤预制棒,特种光纤,海缆',
           2, '光纤预制棒、光纤和光缆制造'
    UNION ALL
    SELECT '光通信产业链', 'DOWNSTREAM', '光通信设备',
           '光通信设备,传输设备,接入设备,OTN,WDM,PON',
           1, '光传输和接入设备研发制造'
    UNION ALL
    SELECT '光通信产业链', 'DOWNSTREAM', '光通信测试仪器',
           '光功率计,光谱分析仪,OTDR,光通信测试',
           2, '光通信测试仪表和解决方案'
    UNION ALL
    SELECT '人工智能产业链', 'UPSTREAM', 'AI芯片/算力',
           'GPU,NPU,TPU,AI芯片,算力,训练芯片,推理芯片,智能加速卡',
           1, 'AI训练和推理芯片'
    UNION ALL
    SELECT '人工智能产业链', 'UPSTREAM', 'AI基础软件',
           'CUDA,框架,推理引擎,编译器,AI编译,训练框架',
           2, 'AI训练推理框架和基础软件栈'
    UNION ALL
    SELECT '人工智能产业链', 'MIDSTREAM', '大模型',
           '大语言模型,多模态,基础模型,预训练,微调,RLHF',
           1, '大语言模型和多模态基础模型研发'
    UNION ALL
    SELECT '人工智能产业链', 'DOWNSTREAM', 'AI应用',
           'AI应用,智能客服,内容生成,代码助手,AI搜索,数字人',
           1, 'AI技术行业应用'
    UNION ALL
    SELECT '人工智能产业链', 'DOWNSTREAM', '智能机器人',
           '人形机器人,工业机器人,服务机器人,具身智能',
           2, '智能机器人本体和核心零部件'
    UNION ALL
    SELECT '人工智能产业链', 'DOWNSTREAM', '自动驾驶',
           '自动驾驶,智能驾驶,激光雷达,毫米波雷达,高精地图',
           3, '自动驾驶技术方案和核心传感器'
) seed ON seed.chain_name = c.chain_name
JOIN chain_node stage
  ON stage.chain_id = c.id
 AND stage.stage_type = seed.stage_type
 AND stage.is_deleted = 0
WHERE c.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM chain_node existing
      WHERE existing.chain_id = c.id
        AND existing.node_name = seed.node_name
        AND existing.is_deleted = 0
  );

UPDATE chain_node child
JOIN chain_node stage ON stage.id = child.parent_id
JOIN chain c ON c.id = child.chain_id
SET child.node_path = CONCAT(stage.node_path, child.id, '/'),
    child.node_level = 2
WHERE c.chain_name IN ('光通信产业链', '人工智能产业链')
  AND child.stage_type IS NULL
  AND stage.stage_type IS NOT NULL
  AND child.is_deleted = 0;

-- ============================================================
-- 2. 标签
-- ============================================================INSERT IGNORE INTO tag (tag_name, tag_type, color, description) VALUES
('高新技术企业', 'SYSTEM', '#1f6fff', '国家高新技术企业认定'),
('专精特新', 'SYSTEM', '#18a66a', '专精特新小巨人企业'),
('上市公司', 'SYSTEM', '#ff9810', 'A股/港股/美股上市公司'),
('独角兽', 'CUSTOM', '#7b61ff', '估值超10亿美元的未上市企业'),
('国产替代', 'SYSTEM', '#ef4444', '关键领域国产替代企业'),
('新能源', 'CUSTOM', '#00A896', '新能源产业链相关企业'),
('半导体', 'CUSTOM', '#2F6BFF', '半导体产业链相关企业'),
('人工智能', 'CUSTOM', '#F59E0B', 'AI及大模型相关企业');

-- ============================================================
-- 3. 新增更多企业
-- ============================================================
-- 3.1 新增车企/出行
INSERT IGNORE INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('浙江吉利控股集团有限公司', '91330000142926389T', '李书福', '浙江杭州', '汽车制造', '杭州市滨江区江陵路1760号', '汽车整车及零部件研发制造', '吉利银河,领克,极氪,沃尔沃,路特斯', 'SEA浩瀚架构,智能驾驶,雷神混动', '吉利,极氪,领克,沃尔沃,新能源汽车,SEA', 2200.00, 95.00, 'ACTIVE'),
('长城汽车股份有限公司', '91130000105941899T', '魏建军', '河北保定', '汽车制造', '保定市朝阳南大街2269号', 'SUV和皮卡整车研发制造', '哈弗,魏牌,坦克,欧拉,长城炮', '柠檬平台,咖啡智能,Hi4-T混动', '长城汽车,哈弗,魏牌,坦克,欧拉,SUV', 1800.00, 75.00, 'ACTIVE'),
('奇瑞汽车股份有限公司', '91340200149444557Q', '尹同跃', '安徽芜湖', '汽车制造', '芜湖市经济技术开发区长春路8号', '整车及发动机研发制造', '奇瑞,星途,捷途,iCAR,奇瑞新能源', '鲲鹏动力,雄狮智驾,火星架构', '奇瑞,星途,捷途,iCAR,新能源汽车', 1500.00, 55.00, 'ACTIVE'),
('广汽埃安新能源汽车股份有限公司', '91440101MA9UMN5U0P', '古惠南', '广东广州', '新能源汽车', '广州市番禺区石楼镇龙瀛路39号', '纯电动汽车研发制造', 'AION S,AION V,AION Y,AION LX,昊铂', '弹匣电池,星灵架构,夸克电驱', '广汽埃安,AION,昊铂,纯电,弹匣电池', 600.00, 18.00, 'ACTIVE');

-- 3.2 新增光通信补充
INSERT IGNORE INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('长飞光纤光缆股份有限公司', '91420000616400489G', '马杰', '湖北武汉', '光纤光缆', '武汉市东湖高新区光谷大道9号', '光纤预制棒、光纤和光缆研发制造', '光纤预制棒,单模光纤,特种光缆,海缆', 'VAD/OVD光纤预制棒技术,超低损耗光纤', '长飞,光纤,光缆,预制棒,海缆', 120.00, 7.50, 'ACTIVE'),
('亨通光电股份有限公司', '91320000607942199R', '钱建林', '江苏苏州', '光纤光缆', '苏州市吴江区中山北路2号', '光纤光缆和海洋通信研发制造', '光纤,光缆,海底光缆,海洋工程', '海底光缆技术,光纤拉丝技术', '亨通光电,光纤,海底光缆,海洋通信', 85.00, 5.00, 'ACTIVE'),
('天孚光通信股份有限公司', '91320000607942243T', '邹支农', '江苏苏州', '光器件', '苏州市工业园区星湖街328号', '光无源器件研发制造', '光纤连接器,陶瓷套筒,光分路器,准直器', '精密陶瓷加工,光学镀膜', '天孚通信,光器件,光纤连接器,陶瓷', 35.00, 2.50, 'ACTIVE');

-- 3.3 新增AI/机器人
INSERT IGNORE INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('科大讯飞股份有限公司', '91340000705016949L', '刘庆峰', '安徽合肥', '人工智能', '合肥市高新区望江西路666号', '智能语音和人工智能技术研发', '讯飞星火大模型,智能语音,翻译机,听见系统', '语音识别,自然语言处理,星火大模型', '科大讯飞,星火大模型,语音识别,NLP,AI', 200.00, 10.00, 'ACTIVE'),
('商汤集团股份有限公司', '91440300MA5EQFWQ3J', '汤晓鸥', '广东深圳', '人工智能', '深圳市南山区海天一路软件产业基地', '计算机视觉和深度学习技术研发', '商汤日日新大模型,人脸识别,智能安防,自动驾驶', '计算机视觉,深度学习,大模型SenseNova', '商汤,计算机视觉,大模型,人脸识别,AI', 50.00, 2.00, 'ACTIVE'),
('深圳市优必选科技股份有限公司', '91440300576390444L', '周剑', '广东深圳', '机器人', '深圳市南山区学府路63号', '智能服务机器人研发制造', 'Walker X人形机器人,悟空机器人,AMR物流机器人', '人形机器人运动控制,SLAM导航,AI交互', '优必选,人形机器人,Walker,服务机器人', 15.00, 0.50, 'ACTIVE'),
('北京地平线机器人技术研发有限公司', '91110108318292985Y', '余凯', '北京', 'AI芯片/自动驾驶', '北京市海淀区中关村软件园', '智能驾驶计算方案研发', '征程系列芯片,智能驾驶计算平台,Horizon SuperDrive', '车规级AI芯片,BPU架构,智能驾驶', '地平线,征程芯片,智能驾驶,BPU,自动驾驶', 30.00, 1.20, 'ACTIVE'),
('云从科技集团股份有限公司', '91440101MA5CYQH29T', '周曦', '广东广州', '人工智能', '广州市南沙区金隆路37号', '人机协同操作系统和AI解决方案', '从容大模型,人机协同操作系统,行业AI解决方案', '人机协同,跨镜追踪,3D结构光', '云从科技,从容大模型,人机协同,AI安防', 10.00, 0.30, 'ACTIVE');

-- 3.4 新增半导体补充
INSERT IGNORE INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('华大九天科技股份有限公司', '91110108663121297Q', '刘伟平', '北京', 'EDA', '北京市海淀区望京东园四区11号楼', 'EDA工具软件研发销售', '模拟电路EDA,数字电路EDA,FPGA EDA,平板显示EDA', '模拟仿真,数字实现,版图设计', '华大九天,EDA,模拟EDA,数字EDA,国产EDA', 12.00, 0.80, 'ACTIVE'),
('杭州安芯微电子有限公司', '91330100MA2GPXXX00', '刘伟峰', '浙江杭州', '芯片设计', '杭州市余杭区仓前街道', '车规级MCU芯片设计销售', '车规级MCU,电机控制MCU,电池管理IC', '车规级芯片设计,功能安全', '车规MCU,汽车芯片,电机控制', 5.00, 0.20, 'ACTIVE');

-- 3.5 新增锂电/储能补充
INSERT IGNORE INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('亿纬锂能股份有限公司', '91441300708117109L', '刘金成', '广东惠州', '动力电池', '惠州市仲恺高新区惠风七路', '锂电池研发制造', '三元锂电池,磷酸铁锂,储能电池,圆柱电池', '大圆柱电池,储能系统集成', '亿纬锂能,动力电池,储能,大圆柱,磷酸铁锂', 500.00, 25.00, 'ACTIVE'),
('欣旺达电子股份有限公司', '914403002794164359', '王明旺', '广东深圳', '动力电池', '深圳市宝安区石岩街道', '锂电池模组和动力电池研发制造', '消费类电池,动力电池,储能系统,电池模组', '电池管理系统,PACK集成', '欣旺达,动力电池,消费电池,储能,PACK', 400.00, 15.00, 'ACTIVE');

-- 3.6 新增新能源/氢能补充
INSERT IGNORE INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('潍柴动力股份有限公司', '91370000164973749T', '谭旭光', '山东潍坊', '动力总成', '潍坊市高新技术产业开发区福寿东街197号', '发动机、变速箱和车桥研发制造', '柴油发动机,天然气发动机,氢燃料电池,变速箱', '大功率发动机技术,氢燃料电池', '潍柴,发动机,氢燃料电池,动力总成,重卡', 2000.00, 80.00, 'ACTIVE'),
('上海重塑能源集团股份有限公司', '91310000MA1FL3YX6P', '林琦', '上海', '氢燃料电池', '上海市嘉定区安亭镇', '氢燃料电池系统研发制造', '氢燃料电池系统,燃料电池电堆,DC-DC', '燃料电池系统集成,石墨双极板', '重塑能源,氢燃料电池,电堆,氢能', 15.00, 0.50, 'ACTIVE'),
('未势能源科技有限公司', '91310000MA1FL8XX8T', '张天羽', '上海', '氢燃料电池', '上海市嘉定区', '氢燃料电池核心零部件研发制造', '燃料电池电堆,膜电极,储氢瓶,氢循环泵', '膜电极技术,碳纤维缠绕储氢', '未势能源,氢能,膜电极,储氢瓶', 8.00, 0.20, 'ACTIVE');

-- 3.7 新增光伏补充
INSERT IGNORE INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('通威股份有限公司', '91510000621706205D', '谢毅', '四川成都', '光伏/农业', '成都市高新区天府大道中段588号', '太阳能电池和饲料研发制造', '太阳能电池片,渔光一体电站,饲料', 'HJT电池技术,TOPCon电池,210mm大尺寸', '通威,太阳能电池,HJT,TOPCon,渔光一体', 1500.00, 80.00, 'ACTIVE'),
('晶科能源股份有限公司', '91360000693632889Q', '李仙德', '江西上饶', '光伏', '上饶经济技术开发区晶科大道1号', '光伏组件和电池研发制造', 'Tiger Neo组件,N型TOPCon电池,储能系统', 'N型TOPCon技术,叠焊技术', '晶科能源,光伏组件,TOPCon,N型电池', 1100.00, 45.00, 'ACTIVE'),
('信义光能控股有限公司', '91440300MA5FXXXX00', '李贤义', '广东深圳', '光伏', '深圳市南山区', '太阳能光伏玻璃和光伏电站', '光伏玻璃,光伏电站,浮法玻璃', '光伏压延玻璃技术,超薄光伏玻璃', '信义光能,光伏玻璃,光伏电站', 200.00, 15.00, 'ACTIVE');

-- ============================================================
-- 4. 企业-标签关联
-- ============================================================
INSERT IGNORE INTO enterprise_tag_rel (enterprise_id, tag_id) VALUES
-- 氢能源链 1-5
(1,1),(1,3),(1,6), (2,1),(2,3),(2,6), (3,1),(3,3),(3,5),
(4,1),(4,3),(4,6), (5,1),(5,6),
-- 光通信 10-13, 49-51
(10,1),(10,3),(10,7), (11,1),(11,3),(11,7), (12,1),(12,3),(12,7), (13,1),(13,3),
(49,1),(49,3), (50,1),(50,3), (51,1),
-- 消费电子 14-19
(14,1),(14,3),(14,4),(14,8), (15,1),(15,4), (16,1),(16,3),(16,5),(16,8),
(17,1),(17,3), (18,1),(18,3), (19,1),(19,3),
-- 新能源汽车 20-25, 46-49
(20,1),(20,3),(20,6), (21,1),(21,3),(21,6), (22,3),(22,6),(22,4),
(23,1),(23,6),(23,4), (24,1),(24,6),(24,4), (25,3),(25,6),
(46,1),(46,3),(46,6), (47,1),(47,3),(47,6), (48,1),(48,3),(48,6),
-- 半导体 26-37, 54-55
(26,1),(26,3),(26,7),(26,5), (27,1),(27,3),(27,7),(27,5), (28,1),(28,8),(28,5),
(29,1),(29,3),(29,7), (30,1),(30,3),(30,7), (31,1),(31,3),(31,7),
(32,1),(32,3),(32,7), (33,1),(33,3),(33,7), (34,1),(34,3),(34,7),
(35,1),(35,7), (36,1),(36,7), (37,1),(37,3),(37,7),
(54,1),(54,5),(54,7), (55,1),(55,7),
-- 光伏储能 38-41, 58-60, 56-57
(38,1),(38,3),(38,6), (39,1),(39,3),(39,6), (40,1),(40,3),(40,6), (41,1),(41,3),(41,6),
(58,1),(58,3),(58,6), (59,1),(59,3),(59,6), (60,3),(60,6),
(56,1),(56,3),(56,6), (57,1),(57,3),(57,6),
-- 互联网/AI 42-45, 52-53, 61-62
(42,3),(42,8), (43,3),(43,8), (44,3),(44,8), (45,4),(45,8),
(52,1),(52,3),(52,8), (53,3),(53,8),
(61,1),(61,8), (62,1),(62,8),
-- AI/机器人 63-67
(63,1),(63,3),(63,8), (64,1),(64,4),(64,8), (65,1),(65,4),(65,8),
(66,1),(66,4),(66,8), (67,1),(67,8);

-- ============================================================
-- 5. 氢能源链企业-节点关联（补充车企）
-- ============================================================
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by) VALUES
(10, 301, 'MANUAL', 1.0, '比亚迪整车制造', 1),
(11, 301, 'MANUAL', 1.0, '上汽集团整车制造', 1),
(12, 301, 'MANUAL', 1.0, '蔚来整车制造', 1),
(13, 301, 'MANUAL', 1.0, '小鹏整车制造', 1),
(14, 301, 'MANUAL', 1.0, '理想汽车整车制造', 1),
(15, 301, 'MANUAL', 1.0, '特斯拉整车制造', 1),
(46, 301, 'MANUAL', 1.0, '吉利汽车整车制造', 1),
(47, 301, 'MANUAL', 1.0, '长城汽车整车制造', 1),
(48, 301, 'MANUAL', 1.0, '奇瑞汽车整车制造', 1),
(49, 301, 'MANUAL', 1.0, '广汽埃安整车制造', 1),
(4, 202, 'AI', 0.95, '宁德时代动力电池', 1),
(5, 203, 'MANUAL', 1.0, '精进电动电驱系统', 1),
(56, 202, 'MANUAL', 1.0, '亿纬锂能动力电池', 1),
(57, 202, 'MANUAL', 1.0, '欣旺达动力电池', 1);

-- ============================================================
-- 6. 半导体链企业-节点关联
-- ============================================================
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 26, id, 'MANUAL', 1.0, '中芯国际晶圆代工', 1 FROM chain_node WHERE chain_id=2 AND node_name='晶圆制造' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 27, id, 'MANUAL', 1.0, '海光信息CPU/DCU芯片设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 28, id, 'MANUAL', 1.0, '寒武纪AI芯片设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 29, id, 'MANUAL', 1.0, '中微半导体刻蚀设备', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体设备' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 30, id, 'MANUAL', 1.0, '北方华创半导体设备', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体设备' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 31, id, 'MANUAL', 1.0, '韦尔半导体图像传感器设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 32, id, 'MANUAL', 1.0, '兆易创新存储/MCU芯片设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 33, id, 'MANUAL', 1.0, '长电科技先进封装', 1 FROM chain_node WHERE chain_id=2 AND node_name='封装测试' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 34, id, 'MANUAL', 1.0, '通富微电封装测试', 1 FROM chain_node WHERE chain_id=2 AND node_name='封装测试' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 35, id, 'MANUAL', 1.0, '沪硅产业半导体硅片材料', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体材料' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 36, id, 'MANUAL', 1.0, '雅克科技光刻胶/前驱体材料', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体材料' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 37, id, 'MANUAL', 1.0, '闻泰科技功率半导体IDM', 1 FROM chain_node WHERE chain_id=2 AND node_name='功率半导体' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 54, id, 'MANUAL', 1.0, '华大九天EDA工具', 1 FROM chain_node WHERE chain_id=2 AND node_name='EDA/IP' AND is_deleted=0 ORDER BY id LIMIT 1;

-- ============================================================
-- 7. 光通信链企业-节点关联
-- ============================================================
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 10, id, 'MANUAL', 1.0, '中际旭创光模块', 1 FROM chain_node WHERE chain_id=3 AND node_name='光模块' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 11, id, 'MANUAL', 1.0, '新易盛光模块', 1 FROM chain_node WHERE chain_id=3 AND node_name='光模块' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 12, id, 'MANUAL', 1.0, '光迅科技光器件', 1 FROM chain_node WHERE chain_id=3 AND node_name='光器件' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 13, id, 'MANUAL', 1.0, '华工科技光通信/激光', 1 FROM chain_node WHERE chain_id=3 AND node_name='光器件' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 49, id, 'MANUAL', 1.0, '长飞光纤光缆', 1 FROM chain_node WHERE chain_id=3 AND node_name='光纤光缆' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 50, id, 'MANUAL', 1.0, '亨通光电光纤光缆', 1 FROM chain_node WHERE chain_id=3 AND node_name='光纤光缆' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 51, id, 'MANUAL', 1.0, '天孚通信光器件', 1 FROM chain_node WHERE chain_id=3 AND node_name='光器件' AND is_deleted=0 ORDER BY id LIMIT 1;

-- ============================================================
-- 8. AI链企业-节点关联
-- ============================================================
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 16, id, 'MANUAL', 1.0, '华为昇腾AI芯片', 1 FROM chain_node WHERE chain_id=4 AND node_name='AI芯片/算力' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 28, id, 'MANUAL', 1.0, '寒武纪AI芯片', 1 FROM chain_node WHERE chain_id=4 AND node_name='AI芯片/算力' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 44, id, 'MANUAL', 1.0, '百度文心大模型', 1 FROM chain_node WHERE chain_id=4 AND node_name='大模型' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 42, id, 'MANUAL', 1.0, '腾讯混元大模型', 1 FROM chain_node WHERE chain_id=4 AND node_name='大模型' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 63, id, 'MANUAL', 1.0, '科大讯飞星火大模型', 1 FROM chain_node WHERE chain_id=4 AND node_name='大模型' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 64, id, 'MANUAL', 1.0, '商汤日日新大模型', 1 FROM chain_node WHERE chain_id=4 AND node_name='大模型' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 65, id, 'MANUAL', 1.0, '优必选人形机器人', 1 FROM chain_node WHERE chain_id=4 AND node_name='智能机器人' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 66, id, 'MANUAL', 1.0, '地平线自动驾驶芯片', 1 FROM chain_node WHERE chain_id=4 AND node_name='自动驾驶' AND is_deleted=0 ORDER BY id LIMIT 1;
INSERT IGNORE INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 44, id, 'MANUAL', 1.0, '百度Apollo自动驾驶', 1 FROM chain_node WHERE chain_id=4 AND node_name='自动驾驶' AND is_deleted=0 ORDER BY id LIMIT 1;
