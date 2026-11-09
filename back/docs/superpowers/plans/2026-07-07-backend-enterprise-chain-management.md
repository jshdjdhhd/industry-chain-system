# 后端企业与产业链管理实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现企业、标签、产业链、节点和企业挂链关系的后端基础管理能力，支撑前端后台页面和智能挂链流程。

**架构：** Controller 暴露 REST 接口，Service 管理业务规则和事务，Mapper 与 XML 负责 MySQL 5.7 兼容查询，OperationLog AOP 记录关键操作。

**技术栈：** Java 21、Spring Boot 3.5.15、MyBatis-Plus、MySQL 5.7、Spring Security、JUnit 5、Mockito、MockMvc、H2。

---

## 文件结构

- 修改：`back/src/main/java/git/ggwzz/enterprise/controller/EnterpriseController.java`。
- 修改：`back/src/main/java/git/ggwzz/enterprise/service/EnterpriseService.java` 与实现类。
- 修改：`back/src/main/java/git/ggwzz/enterprise/service/ImportService.java` 与实现类。
- 修改：`back/src/main/java/git/ggwzz/chain/controller/ChainController.java`。
- 修改：`back/src/main/java/git/ggwzz/chain/controller/ChainNodeController.java`。
- 修改：`back/src/main/java/git/ggwzz/chain/service/ChainService.java` 与实现类。
- 修改：`back/src/main/java/git/ggwzz/chain/service/ChainNodeQueryService.java` 与实现类。
- 修改：`back/src/main/java/git/ggwzz/chain/service/EnterpriseNodeRelService.java` 与实现类。
- 测试：`back/src/test/java/git/ggwzz/enterprise/controller/EnterpriseControllerTest.java`。
- 测试：`back/src/test/java/git/ggwzz/enterprise/service/EnterpriseServiceTest.java`。
- 测试：`back/src/test/java/git/ggwzz/enterprise/service/ImportServiceTest.java`。
- 测试：`back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java`。
- 测试：`back/src/test/java/git/ggwzz/chain/controller/ChainNodeControllerTest.java`。
- 测试：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`。
- 测试：`back/src/test/java/git/ggwzz/chain/service/EnterpriseNodeRelServiceTest.java`。

## 任务 1：企业接口与状态流转

- [ ] **步骤 1：编写企业 Controller 测试**

覆盖分页、选项、详情、新增、编辑、删除、入库、出库、批量入库、批量出库、标签绑定、标签解绑、导入和导入记录查询。

- [ ] **步骤 2：编写企业 Service 测试**

覆盖未入库、已入库、已出库状态之间的允许流转，删除限制，批量操作和标签关系。

- [ ] **步骤 3：实现企业业务**

在 `EnterpriseServiceImpl` 中实现状态校验、事务、唯一性校验和 VO 组装。

- [ ] **步骤 4：运行企业测试**

```bash
cd back
mvn -Dtest=EnterpriseControllerTest,EnterpriseServiceTest,ImportServiceTest test
```

预期：企业接口和业务规则测试通过。

## 任务 2：产业链和节点接口

- [ ] **步骤 1：编写产业链 Controller 测试**

覆盖产业链列表、详情、创建、更新、删除和统一响应。

- [ ] **步骤 2：编写节点 Controller 测试**

覆盖节点树、节点创建、更新、移动、排序、挂链和解绑。

- [ ] **步骤 3：实现节点业务**

在 `ChainNodeQueryServiceImpl` 与相关 Service 中实现树查询、移动校验、排序保存和挂链关系维护。

- [ ] **步骤 4：运行产业链测试**

```bash
cd back
mvn -Dtest=ChainControllerTest,ChainNodeControllerTest,ChainServiceTest,ChainNodeQueryServiceImplTest,EnterpriseNodeRelServiceTest test
```

预期：产业链和节点业务测试通过。

## 任务 3：Mapper 与数据库兼容

- [ ] **步骤 1：编写 Mapper 测试**

覆盖企业选项查询、节点统计、挂链关系查询、Dashboard 汇总和操作日志查询。

- [ ] **步骤 2：校准 SQL**

Mapper XML 明确列名，使用 `#{}` 参数，统计使用 `COUNT(*)` 与 `IFNULL(SUM(...), 0)`，避免 MySQL 8 专属语法。

- [ ] **步骤 3：运行 Mapper 测试**

```bash
cd back
mvn -Dtest=EnterpriseOptionMapperTest,ChainOptionMapperTest,EnterpriseNodeRelMapperTest,DashboardMapperTest test
```

预期：数据库访问测试通过。

## 任务 4：权限与操作日志

- [ ] **步骤 1：补充权限测试**

在 Controller 测试中覆盖无权限访问返回 403，未认证访问返回 401。

- [ ] **步骤 2：添加操作日志注解**

在企业入库、出库、批量操作、导入、节点移动、节点排序、挂链和解绑接口上添加 `@OperationLog`。

- [ ] **步骤 3：运行安全与日志测试**

```bash
cd back
mvn -Dtest=SecurityFlowTest,OperationLogServiceTest test
```

预期：安全流程和操作日志测试通过。

## 任务 5：最终验证

- [ ] **步骤 1：运行业务相关测试**

```bash
cd back
mvn -Dtest=EnterpriseControllerTest,EnterpriseServiceTest,ImportServiceTest,ChainControllerTest,ChainNodeControllerTest,ChainServiceTest,EnterpriseNodeRelServiceTest test
```

预期：企业与产业链相关测试通过。

- [ ] **步骤 2：运行后端全量测试**

```bash
cd back
mvn test
```

预期：后端全量测试通过。

- [ ] **步骤 3：运行打包**

```bash
cd back
mvn package
```

预期：后端应用打包成功。
