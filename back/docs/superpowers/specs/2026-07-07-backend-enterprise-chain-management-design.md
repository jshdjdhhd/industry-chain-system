# 后端企业与产业链管理设计

> 本文件由 Codex 按 superpowers:brainstorming 规格文档形态生成，用于记录后端企业、标签与产业链基础管理能力的 Spec Coding 需求。

## 背景

产业链管理系统的核心数据包括企业、标签、产业链、节点和企业节点关系。后端已有 `enterprise`、`tag`、`chain`、`dashboard` 和 `operationlog` 模块，前端企业管理、产业链管理、节点调整、数据看板和操作日志页面均依赖这些接口。

## 目标

- 支持企业分页查询、详情、新增、编辑、删除、入库、出库、批量入库、批量出库、导入和导入记录查询。
- 支持企业标签绑定与解绑。
- 支持产业链和节点的查询、创建、更新、移动、排序、挂链和解绑。
- 支持 Dashboard 汇总企业、产业链、节点和推荐结果数据。
- 支持关键操作写入操作日志并可查询。
- 所有接口使用统一响应和权限控制。

## 非目标

- 不让前端绕过后端直接维护关联表。
- 不使用数据库外键或级联删除。
- 不使用 MySQL 8 专属语法。
- 不在 Controller 中编写业务规则。

## 企业模块设计

- `EnterpriseController` 暴露企业 CRUD、入库出库、批量操作、导入和标签绑定接口。
- `EnterpriseService` 负责企业状态流转、唯一性校验、标签关系维护和事务。
- `ImportService` 负责 Excel/CSV 导入、导入记录和错误统计。
- `EnterpriseMapper` 与 `EnterpriseTagRelMapper` 负责数据访问。
- 企业状态至少覆盖未入库、已入库和已出库。

## 产业链模块设计

- `ChainController` 负责产业链列表、详情、创建、更新和删除。
- `ChainNodeController` 负责节点树、节点创建、更新、移动、排序、挂链和解绑。
- `ChainService` 管理产业链基础信息。
- `ChainNodeQueryService` 提供节点树和候选节点查询。
- `EnterpriseNodeRelService` 管理企业与节点关系。
- 树形数据使用 `parent_id`、`node_path`、`level_no` 和 `sort_no` 维护，不依赖递归 CTE。

## 数据与事务

- 企业入库、出库、批量入库、批量出库、标签绑定、标签解绑、节点移动、节点排序、企业挂链和解绑均在 Service 层使用事务。
- 删除使用逻辑删除。
- 更新操作同步 `updated_at`。
- Mapper XML 明确列名，禁止 `SELECT *`。

## 权限与审计

- 企业查询、创建、更新、删除、入库、出库、导入、标签绑定和解绑分别配置权限。
- 产业链和节点创建、更新、删除、移动、排序、挂链和解绑分别配置权限。
- 入库、出库、批量操作、导入、节点变更、挂链、解绑等关键操作写入操作日志。

## 测试策略

- `EnterpriseControllerTest` 覆盖企业接口统一响应、权限和参数校验。
- `EnterpriseServiceTest` 覆盖企业状态流转、删除规则、批量入库出库、标签绑定。
- `ImportServiceTest` 覆盖导入成功、字段错误和重复数据。
- `ChainControllerTest` 与 `ChainNodeControllerTest` 覆盖产业链和节点接口。
- `ChainServiceTest`、`ChainNodeQueryServiceImplTest`、`EnterpriseNodeRelServiceTest` 覆盖业务规则。
- Mapper 测试覆盖 XML 查询和统计结果。

## 验收标准

- 企业管理、标签绑定、产业链节点和挂链接口均返回统一 `Result`。
- 企业状态流转规则由后端保证。
- 节点移动不能形成环，排序结果连续稳定。
- 关键写操作有事务和操作日志。
- 相关测试通过：`mvn -Dtest=EnterpriseServiceTest,EnterpriseControllerTest,ChainServiceTest,ChainNodeControllerTest,EnterpriseNodeRelServiceTest test`。
