-- =============================================
-- 1. 清理测试数据
-- =============================================
UPDATE enterprise SET is_deleted=1 WHERE enterprise_name='水水水水';
UPDATE chain SET is_deleted=1 WHERE chain_name='test-chain';

-- =============================================
-- 2. 半导体产业链阶段与细分节点
-- =============================================
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
WHERE c.id = 2
  AND c.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM chain_node existing
      WHERE existing.chain_id = c.id
        AND existing.stage_type = s.stage_type
        AND existing.is_deleted = 0
  );

UPDATE chain_node
SET node_path = CONCAT('/', id, '/'),
    node_level = 1,
    parent_id = NULL
WHERE chain_id = 2
  AND stage_type IS NOT NULL
  AND is_deleted = 0;

INSERT INTO chain_node
    (chain_id, parent_id, node_name, keywords, node_level,
     node_path, sort_order, description)
SELECT c.id, stage.id, seed.node_name, seed.keywords, 2,
       '/', seed.sort_order, seed.description
FROM chain c
JOIN (
    SELECT 'UPSTREAM' stage_type, '芯片设计' node_name,
           'IC设计,EDA,IP核,SoC,ASIC,FPGA,数字芯片,模拟芯片' keywords,
           1 sort_order, '集成电路设计环节' description
    UNION ALL
    SELECT 'UPSTREAM', '半导体设备',
           '光刻机,刻蚀机,薄膜设备,检测设备,半导体装备',
           2, '半导体制造设备环节'
    UNION ALL
    SELECT 'UPSTREAM', '半导体材料',
           '硅片,光刻胶,电子气体,靶材,CMP,掩膜版',
           3, '半导体材料环节'
    UNION ALL
    SELECT 'UPSTREAM', 'EDA/IP',
           'EDA工具,IP核,设计自动化,仿真验证',
           4, 'EDA工具和IP核授权'
    UNION ALL
    SELECT 'MIDSTREAM', '晶圆制造',
           '晶圆代工,光刻,刻蚀,薄膜沉积,半导体制造,先进制程',
           1, '晶圆代工制造环节'
    UNION ALL
    SELECT 'MIDSTREAM', '封装测试',
           '封测,先进封装,测试,Chiplet,2.5D封装,3D封装',
           2, '芯片封装与测试环节'
    UNION ALL
    SELECT 'MIDSTREAM', '功率半导体',
           'IGBT,MOSFET,SiC,GaN,功率器件,第三代半导体',
           3, '功率半导体器件'
) seed
JOIN chain_node stage
  ON stage.chain_id = c.id
 AND stage.stage_type = seed.stage_type
 AND stage.is_deleted = 0
WHERE c.id = 2
  AND c.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM chain_node existing
      WHERE existing.chain_id = c.id
        AND existing.node_name = seed.node_name
        AND existing.is_deleted = 0
  );

UPDATE chain_node child
JOIN chain_node stage ON stage.id = child.parent_id
SET child.node_path = CONCAT(stage.node_path, child.id, '/'),
    child.node_level = 2
WHERE child.chain_id = 2
  AND child.stage_type IS NULL
  AND stage.stage_type IS NOT NULL
  AND child.is_deleted = 0;

-- =============================================
-- 3. 光通信企业
-- =============================================INSERT INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('中际旭创股份有限公司', '91320500608281896C', '刘圣', '江苏苏州', '光通信', '苏州市工业园区星湖街328号', '光模块、光器件研发生产和销售', '高速光模块,400G/800G光模块,数据中心互联', '硅光技术,相干光通信,高速光模块封装', '光模块,高速光模块,光通信,数据中心,400G,800G', 150.00, 8.50, 'ACTIVE'),
('成都新易盛通信技术股份有限公司', '91510100667852587Q', '高光荣', '四川成都', '光通信', '成都市高新区天欣路99号', '光模块及光器件研发生产和销售', '光模块,光收发器件,数据中心光模块,5G前传光模块', '高速光信号处理,光模块自动化生产', '光模块,光通信,5G前传,数据中心', 80.00, 5.20, 'ACTIVE'),
('武汉光迅科技股份有限公司', '91420100737525595Q', '黄宣泽', '湖北武汉', '光通信', '武汉市东湖高新区光谷大道66号', '光电子器件和光模块研发制造', '光器件,光模块,光纤放大器,WDM器件', '光芯片制造,PLC平台,光器件集成', '光器件,光芯片,光通信,PLC,WDM', 65.00, 4.10, 'ACTIVE'),
('武汉华工科技产业股份有限公司', '91420000271756489F', '马新强', '湖北武汉', '光通信/激光', '武汉市东湖高新区华中科技大学科技园', '激光加工设备和光通信器件研发制造', '激光切割设备,光通信器件,温度传感器', '高功率激光器,光纤激光器,光通信芯片', '激光,光通信,传感器,光纤激光器', 120.00, 7.80, 'ACTIVE');

-- =============================================
-- 4. 消费电子企业
-- =============================================
INSERT INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('小米科技有限责任公司', '91110108551385082Q', '雷军', '北京', '消费电子/智能汽车', '北京市海淀区清河中街68号', '智能手机、IoT设备、智能电动汽车、互联网服务', '小米手机,小米SU7,小米平板,小米手环,智能家居', '澎湃芯片,智能驾驶,HyperOS', '小米,智能手机,智能汽车,IoT,HyperOS,SU7', 2800.00, 85.00, 'ACTIVE'),
('深圳酷态科技术有限公司', '91440300MA5G5T5Q57', '陈勇', '广东深圳', '消费电子/储能', '深圳市南山区粤海街道科技园', '移动电源和储能设备研发销售', '移动电源,户外储能电源,氮化镓充电器,数据线', '氮化镓快充技术,电池管理系统,智能温控', '移动电源,充电器,储能,氮化镓,快充,酷态科', 25.00, 1.80, 'ACTIVE'),
('华为技术有限公司', '9144030027996948XT', '梁华', '广东深圳', '通信/消费电子', '深圳市龙岗区坂田华为基地', '通信设备、智能手机、云计算、智能汽车', '华为手机,5G基站,昇腾AI芯片,鸿蒙系统,华为云', '5G通信技术,昇腾AI,鸿蒙OS,麒麟芯片', '华为,5G,鸿蒙,昇腾,麒麟,智能驾驶,ICT', 7000.00, 650.00, 'ACTIVE'),
('OPPO广东移动通信有限公司', '91441900773059001J', '陈明永', '广东东莞', '消费电子', '东莞市长安镇乌沙海滨路18号', '智能手机和智能穿戴设备研发销售', 'OPPO手机,一加手机,OPPO手表,OPPO耳机', '闪充技术,影像算法,ColorOS', 'OPPO,一加,闪充,智能手机,影像', 1500.00, 45.00, 'ACTIVE'),
('维沃移动通信有限公司', '914419006997636070', '沈炜', '广东东莞', '消费电子', '东莞市长安镇维沃路1号', '智能手机和智能穿戴设备研发销售', 'vivo手机,iQOO手机,vivo手表,vivo耳机', '影像芯片V系列,OriginOS,微云台防抖', 'vivo,iQOO,智能手机,影像,微云台', 1400.00, 42.00, 'ACTIVE'),
('联想集团有限公司', '91110000100018695C', '杨元庆', '北京', '计算机/消费电子', '北京市海淀区上地信息路6号', '个人电脑、服务器、智能设备研发制造', 'ThinkPad,联想笔记本,服务器,工作站,平板电脑', '供应链管理,混合AI,企业级解决方案', '联想,ThinkPad,笔记本,服务器,PC', 4500.00, 120.00, 'ACTIVE');

-- =============================================
-- 5. 新能源汽车企业
-- =============================================
INSERT INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('比亚迪股份有限公司', '91440300192507104G', '王传福', '广东深圳', '新能源汽车', '深圳市坪山区比亚迪路3003号', '新能源汽车、动力电池、半导体研发制造', '比亚迪汉,比亚迪唐,刀片电池,DM-i超级混动', '刀片电池,DM-i混动技术,IGBT芯片', '比亚迪,新能源汽车,刀片电池,DM-i,IGBT', 6000.00, 200.00, 'ACTIVE'),
('上海汽车集团股份有限公司', '91310000132208068B', '陈虹', '上海', '汽车制造', '上海市浦东新区松涛路100号', '整车及零部件研发制造销售', '荣威,名爵,智己汽车,飞凡汽车,上汽大通', '智能驾驶,新能源三电系统,电子架构', '上汽,荣威,名爵,智己,飞凡', 7500.00, 280.00, 'ACTIVE'),
('蔚来集团', '91310000MA1FL5G80T', '李斌', '上海', '新能源汽车', '上海市嘉定区安亭镇安拓路56号', '智能电动汽车研发制造销售', '蔚来ES6,蔚来ET7,蔚来ES8,换电站', '换电技术,智能座舱,自动驾驶NAD', '蔚来,新能源汽车,换电,智能驾驶', 550.00, 12.00, 'ACTIVE'),
('广州小鹏汽车科技有限公司', '91440101MA5AXH0N3P', '何小鹏', '广东广州', '新能源汽车', '广州市天河区岑村松岗大街8号', '智能电动汽车研发制造销售', '小鹏G6,小鹏P7,小鹏X9,飞行汽车', '智能驾驶XNGP,飞行汽车,800V超充', '小鹏,新能源汽车,智能驾驶,XNGP', 300.00, 8.00, 'ACTIVE'),
('理想汽车', '91110113MA01LN0K7R', '李想', '北京', '新能源汽车', '北京市顺义区高丽营镇恒兴路4号', '智能电动汽车研发制造销售', '理想L9,理想L8,理想L7,理想MEGA', '增程式电动技术,智能座舱,AD Max', '理想,新能源汽车,增程式,SUV', 800.00, 25.00, 'ACTIVE'),
('特斯拉(中国)', '91310000MA1FL1CC48', '朱晓彤', '上海', '新能源汽车', '上海市浦东新区南汇新城镇同汇路168号', '电动汽车及能源产品研发制造', 'Model 3,Model Y,Powerwall,Megapack', '一体压铸,纯视觉FSD,4680电池', '特斯拉,Model 3,Model Y,FSD', 2000.00, 60.00, 'ACTIVE');

-- =============================================
-- 6. 半导体企业
-- =============================================
INSERT INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('中芯国际集成电路制造有限公司', '91310000607288433T', '刘训峰', '上海', '半导体制造', '上海市浦东新区张江路18号', '集成电路晶圆代工及相关服务', '14nm/28nm晶圆代工,BCD工艺,FinFET', '先进制程工艺,FinFET技术,特色工艺', '中芯国际,晶圆代工,半导体制造,14nm,28nm', 500.00, 18.00, 'ACTIVE'),
('海光信息技术股份有限公司', '91120116MA06L5TQ8F', '沙超群', '天津', '芯片设计', '天津市西青区海泰华科九路9号', '高端处理器及配套芯片研发销售', '海光CPU,海光DCU,海光芯片', 'x86架构授权,高端处理器设计', '海光,CPU,DCU,服务器芯片,x86', 80.00, 5.50, 'ACTIVE'),
('寒武纪科技股份有限公司', '91110108MA004YR137', '陈天石', '北京', 'AI芯片', '北京市海淀区知春路7号致真大厦', '人工智能芯片及基础软件研发销售', '思元系列AI芯片,寒武纪MLU,智能加速卡', 'AI芯片架构,MLU指令集,智能计算', '寒武纪,AI芯片,思元,MLU,人工智能', 15.00, 0.80, 'ACTIVE'),
('中微半导体设备股份有限公司', '91310000607288572F', '尹志尧', '上海', '半导体设备', '上海市浦东新区金海路1357号', '半导体设备及泛半导体设备研发制造', 'CCP刻蚀设备,ICP刻蚀设备,MOCVD设备', '等离子体刻蚀技术,MOCVD技术', '中微,刻蚀机,半导体设备,MOCVD', 60.00, 4.50, 'ACTIVE'),
('北方华创科技集团股份有限公司', '91110000710931567Q', '赵晋荣', '北京', '半导体设备', '北京市朝阳区酒仙桥东路1号', '半导体装备和真空装备研发制造', 'PVD设备,CVD设备,刻蚀设备,清洗设备', 'PVD溅射技术,CVD薄膜沉积技术', '北方华创,半导体设备,PVD,CVD,刻蚀', 180.00, 12.00, 'ACTIVE'),
('韦尔半导体股份有限公司', '913100006072883377', '虞仁荣', '上海', '芯片设计', '上海市浦东新区上科路88号', '半导体器件和电源管理IC设计销售', 'CMOS图像传感器,模拟芯片,电源管理IC', 'CMOS图像传感器设计,ISP图像处理', '韦尔,CIS,图像传感器,模拟芯片', 200.00, 15.00, 'ACTIVE'),
('兆易创新科技集团股份有限公司', '911101087988310247', '朱一明', '北京', '芯片设计', '北京市海淀区丰豪东路9号', '存储器、MCU和传感器研发销售', 'NOR Flash,DRAM,MCU,指纹传感器', '存储芯片设计,RISC-V MCU', '兆易创新,NOR Flash,MCU,存储芯片', 80.00, 6.00, 'ACTIVE'),
('长电科技股份有限公司', '91320000607942148D', '郑力', '江苏江阴', '封测', '江阴市滨江中路288号', '集成电路封装设计和产品开发', 'FC-BGA封装,SiP封装,Chiplet封装', '先进封装技术,FC-BGA,SiP,Chiplet', '长电科技,封装测试,先进封装,FC-BGA', 300.00, 18.00, 'ACTIVE'),
('通富微电子股份有限公司', '91320000607942180T', '石磊', '江苏南通', '封测', '南通市崇川路288号', '集成电路封装测试服务', 'BGA封装,QFN封装,FC封装,SiP模块', '大规模封装技术,高密度互联', '通富微电,封测,BGA,QFN', 180.00, 10.00, 'ACTIVE'),
('沪硅产业集团股份有限公司', '91310000MA1FL3YX6P', '李晓忠', '上海', '半导体材料', '上海市浦东新区合庆镇瑞庆路59号', '半导体硅片研发生产和销售', '300mm硅片,200mm硅片,SOI硅片', '大尺寸硅片制造技术,SOI技术', '沪硅产业,硅片,300mm,SOI,半导体材料', 30.00, 1.50, 'ACTIVE'),
('雅克科技', '913200006079423008', '沈琦', '江苏宜兴', '半导体材料', '宜兴市经济开发区', '电子化学品和光刻胶研发销售', '光刻胶,前驱体,电子特气,SOD/SOC', '光刻胶配方技术,前驱体合成技术', '雅克科技,光刻胶,前驱体,电子特气', 45.00, 3.00, 'ACTIVE'),
('闻泰科技股份有限公司', '913300006079422855', '张学政', '浙江嘉兴', '半导体/ODM', '嘉兴市南湖区亚太路1号', '半导体IDM和智能终端ODM', '功率半导体,安世半导体,手机ODM', 'GaN功率器件,IGBT技术', '闻泰,安世,功率半导体,GaN,IGBT', 600.00, 25.00, 'ACTIVE');

-- =============================================
-- 7. 光伏/储能/锂电企业
-- =============================================
INSERT INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('隆基绿能科技股份有限公司', '91610000737998695N', '钟宝申', '陕西西安', '光伏', '西安市经开区尚稷路888号', '单晶硅棒、硅片、电池和组件研发制造', '单晶硅片,BC电池,Hi-MO组件', 'HPBC电池技术,泰睿硅片,BC技术', '隆基,光伏,单晶硅片,BC电池,组件', 1200.00, 50.00, 'ACTIVE'),
('阳光电源股份有限公司', '91340100149240288Q', '曹仁贤', '安徽合肥', '光伏/储能', '合肥市高新区习友路1699号', '光伏逆变器和储能系统研发制造', '光伏逆变器,储能系统,风电变流器', '光伏逆变技术,储能系统集成', '阳光电源,逆变器,储能,光伏,风电', 800.00, 55.00, 'ACTIVE'),
('天合光能股份有限公司', '913200006079422277', '高纪凡', '江苏常州', '光伏', '常州市新北区天合路2号', '光伏组件及智慧能源解决方案', '至尊系列组件,210mm组件,跟踪支架', '210mm大尺寸硅片技术,IBC电池', '天合光能,光伏组件,210mm,跟踪支架', 1000.00, 40.00, 'ACTIVE'),
('国轩高科股份有限公司', '91340100691056489P', '李缜', '安徽合肥', '动力电池', '合肥市包河区花园大道999号', '动力电池及储能电池研发制造', '磷酸铁锂电池,三元锂电池,半固态电池', '磷酸铁锂技术,半固态电池技术', '国轩高科,磷酸铁锂,动力电池,储能', 300.00, 12.00, 'ACTIVE');

-- =============================================
-- 8. 互联网/软件企业
-- =============================================
INSERT INTO enterprise (enterprise_name, unified_code, legal_person, region, industry, address, business_scope, main_products, core_tech, keywords, output_value, tax_amount, status) VALUES
('腾讯科技(深圳)有限公司', '914403007708833548', '马化腾', '广东深圳', '互联网', '深圳市南山区海天二路33号', '互联网增值服务、云计算、金融科技', '微信,QQ,腾讯云,腾讯游戏,企业微信', '即时通信技术,云计算,游戏引擎,AI大模型', '腾讯,微信,QQ,云计算,游戏,社交', 6000.00, 400.00, 'ACTIVE'),
('阿里巴巴(中国)有限公司', '91330100799225998B', '张勇', '浙江杭州', '互联网', '杭州市余杭区文一西路969号', '电子商务、云计算、数字娱乐', '淘宝,天猫,阿里云,钉钉,菜鸟', '云计算技术,分布式数据库,推荐算法', '阿里巴巴,淘宝,天猫,阿里云,电商', 8500.00, 500.00, 'ACTIVE'),
('百度在线网络技术(北京)有限公司', '911101087177448918', '李彦宏', '北京', '互联网/AI', '北京市海淀区上地十街10号', '搜索引擎、人工智能、云计算、智能驾驶', '百度搜索,文心一言,百度云,Apollo', '文心大模型,Apollo自动驾驶,PaddlePaddle', '百度,搜索,文心一言,Apollo,智能驾驶', 1200.00, 80.00, 'ACTIVE'),
('北京字节跳动科技有限公司', '911101085923485114', '张利东', '北京', '互联网', '北京市海淀区北三环西路甲18号', '互联网信息、短视频、企业服务', '抖音,今日头条,飞书,豆包AI,剪映', '推荐算法,短视频技术,飞书协同,AI大模型', '字节跳动,抖音,今日头条,飞书,豆包', 8000.00, 300.00, 'ACTIVE');

-- =============================================
-- 9. 标签
-- =============================================
INSERT INTO tag (tag_name, tag_type, color, description) VALUES
('高新技术企业', 'SYSTEM', '#1f6fff', '国家高新技术企业认定'),
('专精特新', 'SYSTEM', '#18a66a', '专精特新小巨人企业'),
('上市公司', 'SYSTEM', '#ff9810', 'A股/港股/美股上市公司'),
('独角兽', 'CUSTOM', '#7b61ff', '估值超10亿美元的未上市企业'),
('国产替代', 'SYSTEM', '#ef4444', '关键领域国产替代企业'),
('新能源', 'CUSTOM', '#00A896', '新能源产业链相关企业'),
('半导体', 'CUSTOM', '#2F6BFF', '半导体产业链相关企业'),
('人工智能', 'CUSTOM', '#F59E0B', 'AI及大模型相关企业');

-- =============================================
-- 10. 企业标签关联
-- =============================================
INSERT INTO enterprise_tag_rel (enterprise_id, tag_id) VALUES
(1,1),(1,3),(1,6), (2,1),(2,3),(2,6), (3,1),(3,3),(3,7),
(4,1),(4,3),(4,6), (5,1),(5,6),
(10,1),(10,3),(10,6), (11,1),(11,3),(11,7),
(12,3),(12,6), (13,1),(13,6), (14,1),(14,6),
(15,1),(15,3),(15,6),
(16,1),(16,3),(16,7), (17,1),(17,3),(17,7), (18,1),(18,8),
(19,1),(19,3),(19,7), (20,1),(20,3),(20,7), (21,1),(21,7),
(22,1),(22,7), (23,1),(23,3),(23,7), (24,1),(24,7),
(25,1),(25,7), (26,1),(26,7), (27,1),(27,3),(27,7),
(28,1),(28,7), (29,1),(29,3),(29,7),
(30,1),(30,3),(30,6), (31,1),(31,3),(31,6), (32,1),(32,3),(32,6),
(33,1),(33,3),(33,6), (34,1),(34,3),(34,6),
(35,3),(35,8), (36,3),(36,8), (37,3),(37,8), (38,3),(38,8);

-- =============================================
-- 11. 氢能源链企业-节点关联
-- =============================================
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by) VALUES
(1, 101, 'MANUAL', 1.0, '隆基氢能主营氢气制取', 1),
(2, 102, 'AI', 0.95, '中石化氢能主营氢气储运', 1),
(3, 201, 'MANUAL', 1.0, '亿华通主营燃料电池', 1),
(4, 202, 'AI', 0.95, '宁德时代主营动力电池', 1),
(5, 203, 'MANUAL', 1.0, '精进电动主营电驱系统', 1),
(10, 301, 'MANUAL', 1.0, '比亚迪整车制造', 1),
(12, 301, 'MANUAL', 1.0, '蔚来整车制造', 1),
(13, 301, 'MANUAL', 1.0, '小鹏整车制造', 1),
(14, 301, 'MANUAL', 1.0, '理想整车制造', 1),
(15, 301, 'MANUAL', 1.0, '特斯拉整车制造', 1),
(11, 301, 'MANUAL', 1.0, '上汽整车制造', 1);

-- =============================================
-- 12. 半导体链企业-节点关联
-- =============================================
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 16, id, 'MANUAL', 1.0, '中芯国际晶圆代工', 1 FROM chain_node WHERE chain_id=2 AND node_name='晶圆制造' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 17, id, 'MANUAL', 1.0, '海光信息CPU/DCU芯片设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 18, id, 'MANUAL', 1.0, '寒武纪AI芯片设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 19, id, 'MANUAL', 1.0, '中微半导体刻蚀设备', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体设备' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 20, id, 'MANUAL', 1.0, '北方华创半导体设备', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体设备' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 21, id, 'MANUAL', 1.0, '韦尔半导体图像传感器设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 22, id, 'MANUAL', 1.0, '兆易创新存储/MCU芯片设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 23, id, 'MANUAL', 1.0, '长电科技先进封装', 1 FROM chain_node WHERE chain_id=2 AND node_name='封装测试' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 24, id, 'MANUAL', 1.0, '通富微电封装测试', 1 FROM chain_node WHERE chain_id=2 AND node_name='封装测试' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 25, id, 'MANUAL', 1.0, '沪硅产业半导体硅片材料', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体材料' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 26, id, 'MANUAL', 1.0, '雅克科技光刻胶/前驱体材料', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体材料' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 27, id, 'MANUAL', 1.0, '闻泰科技功率半导体IDM', 1 FROM chain_node WHERE chain_id=2 AND node_name='功率半导体' AND is_deleted=0 LIMIT 1;
