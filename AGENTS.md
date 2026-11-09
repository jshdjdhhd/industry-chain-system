# 产业链管理系统 AI 协作说明书

## 项目概述

本项目是一个前后端分离的产业链管理系统，由前端仓库 `front` 和后端仓库 `back` 组成。系统面向企业库管理、产业链节点维护、企业挂链、智能匹配、AI 分析、分析报告、数据看板、系统权限和操作日志等业务场景。

前端仓库名：

```text
front
```

后端仓库名：

```text
back
```

提交作业时重点提交 `front` 和 `back` 两个 Git 仓库中的内容，其他辅助目录不作为正式项目代码提交。

### 前端功能

- 登录与后台布局。
- 企业管理、企业详情、企业导入记录和标签管理。
- 产业链管理、节点树展示、节点移动和同级排序。
- 智能挂链工作台，支持批量任务、单企业分析、候选结果采纳、拒绝和人工确认。
- 分析报告列表、报告详情和产业链诊断。
- 数据看板、系统管理和操作日志。

### 后端功能

- Spring Security + JWT 登录认证和权限控制。
- 企业、标签、产业链、节点和企业节点关系管理。
- 智能挂链任务、批量挂链任务、AI 对话分析和匹配评测。
- 分析报告、Dashboard 汇总和操作日志。
- 统一响应、统一异常、参数校验、事务和审计日志。

## 技术栈与环境要求

### 前端技术栈

- Vue 3，Composition API，`<script setup>`
- TypeScript
- Vite 5
- Vue Router 4
- Pinia
- Element Plus
- Axios
- Vitest 与 `@vue/test-utils`
- Prettier

前端环境要求：

- Node.js 20 或兼容当前依赖的 LTS 版本。
- npm，依赖版本以 `front/package-lock.json` 为准。
- 开发服务器默认端口为 `5173`。
- 后端联调默认地址为 `http://localhost:8080`。

### 后端技术栈

- JDK 21
- Spring Boot 3.5.15
- Maven 3.8+
- MyBatis-Plus 3.5.7
- MySQL 5.7，测试环境使用 H2
- Spring Security + JWT
- Jakarta Validation
- Lombok
- SLF4J + Logback
- JUnit 5、Mockito、Spring Boot Test、MockMvc
- smart-doc

后端环境要求：

- JDK 21。
- Maven 3.8 或更高版本。
- MySQL 5.7。
- 服务默认端口为 `8080`。
- 前端联调来源默认为 `http://localhost:5173`。
- 数据库密码、JWT 密钥、外部模型 Key 等敏感配置必须通过环境变量或不入库配置提供。

## 构建与测试命令

### 前端命令

在 `front` 仓库根目录执行：

```bash
npm install
npm run dev
npm test
npm run build
npm run format:check
```

命令说明：

- `npm run dev`：启动 Vite 开发服务器。
- `npm test`：运行 Vitest 单元测试和组件测试。
- `npm run build`：执行 TypeScript 类型检查并生成生产构建。
- `npm run format:check`：检查 Prettier 格式。
- 如需临时启用本地 Mock，可设置 `VITE_API_MOCK=true`。

### 后端命令

在 `back` 仓库根目录执行：

```bash
mvn test
mvn package
mvn spring-boot:run -Dspring-boot.run.profiles=local
mvn smart-doc:html
```

命令说明：

- `mvn test`：运行后端单元测试、MockMvc 测试和基于 H2 的集成测试。
- `mvn package`：执行测试并打包应用。
- `mvn spring-boot:run -Dspring-boot.run.profiles=local`：使用本地 profile 启动服务。
- `mvn smart-doc:html`：生成接口文档。

## 代码风格指南

### 前端代码风格

- 页面组件放在 `front/src/views/`，按业务模块分目录。
- 通用组件放在 `front/src/components/`，不得耦合具体业务状态流转。
- API 请求统一通过 `front/src/api/` 封装，组件不得直接调用 Axios。
- 全局或跨页面状态放在 `front/src/stores/`，组件通过 action 修改状态。
- 类型定义集中在 `front/src/types/domain.ts`，接口字段需要与后端响应保持一致。
- 路由集中在 `front/src/router/index.ts`，后台页面挂载在 `AdminLayout` 下。
- 组件文件名使用大驼峰，例如 `EnterpriseList.vue`。
- 变量和函数使用小驼峰，常量使用全大写下划线。
- 路由 path 使用小写短横线。
- Vue 文件结构保持为 `<template>`、`<script setup lang="ts">`、`<style scoped>`。
- 禁止保留调试 `console.log`。
- 列表页需要包含 loading、空状态和错误反馈。
- 删除、出库、撤回等风险操作需要二次确认。
- 单行长度控制在 120 字符以内，缩进使用 2 个空格，文件编码 UTF-8。

### 后端代码风格

- 根包固定为 `git.ggwzz`。
- 业务模块按 `controller / service / service.impl / mapper / entity / dto / vo` 分层。
- Controller 只负责接收参数、校验、调用 Service 和返回统一响应，禁止直接调用 Mapper。
- Service 负责业务规则、状态流转和事务，不返回 `Result`。
- Entity 只对应数据库表，接口响应使用 VO，请求参数使用 DTO。
- 超过两个业务参数时使用 DTO 或 Query 对象，禁止用 `Map` 承载正式接口参数。
- 数据库表名和字段名使用小写下划线。
- Java 类名使用 UpperCamelCase，方法和变量使用 lowerCamelCase，常量使用全大写下划线。
- SQL 参数使用 `#{}`，禁止用 `${}` 拼接用户输入。
- 统一响应结构为 `{ code, message, data }`，成功码为 `200`。
- 分页请求使用 `pageNum/pageSize`，响应使用 `records/total/pageNum/pageSize`。
- 关键写操作使用 `@Transactional(rollbackFor = Exception.class)`。
- 禁止返回 SQL、堆栈、服务器路径、Token、密码、JWT 密钥等敏感信息。
- 日志使用 SLF4J 占位符，禁止 `System.out.println` 和 `printStackTrace()`。
- 单行长度控制在 120 字符以内，缩进使用 4 个空格，文件编码 UTF-8。

## 测试说明

### 前端测试

前端测试框架为 Vitest、jsdom 与 `@vue/test-utils`。测试文件与被测文件同目录，命名为 `*.spec.ts`。

前端验收标准：

- API 封装测试覆盖请求路径、请求方法、参数和响应映射。
- ViewModel 测试覆盖任务状态、候选结果、风险提示和操作日志文本。
- 页面组件测试覆盖关键文案、按钮状态、空状态和主要用户操作。
- 路由和 Store 测试覆盖权限跳转、菜单状态和跨页面状态。
- 提交前至少运行 `npm test`。
- 涉及路由、类型、构建配置或公共类型时，同时运行 `npm run build`。

### 后端测试

后端测试位于 `back/src/test/java`，类名使用 `被测类名 + Test`。测试框架为 JUnit 5、Mockito、Spring Boot Test 和 MockMvc。

后端验收标准：

- Service 单测使用 Mockito 隔离 Mapper、外部模型和异步执行器。
- Controller 测试使用 MockMvc 验证权限、状态码、参数校验和统一响应格式。
- Security 测试覆盖匿名访问、登录成功、Token 缺失、Token 失效和无权限访问。
- 数据库相关测试使用 H2 与 `src/test/resources` 中的 schema/data，保持自动化、独立、可重复。
- 核心业务需要覆盖正确流程、边界值和异常流程。
- 提交前至少运行 `mvn test`。
- 涉及打包、资源、迁移脚本、依赖或启动配置时，同时运行 `mvn package`。

## Spec Coding 产物要求

本项目的 Spec Coding 产物按 superpowers 目录约定保存：

```text
docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md
docs/superpowers/plans/YYYY-MM-DD-<topic>.md
```

前端仓库应包含前端相关需求规格和实现计划，例如：

- 智能挂链工作台。
- 产业链节点调整。

后端仓库应包含后端相关需求规格和实现计划，例如：

- 智能挂链与 AI 分析。
- 企业与产业链管理。

Spec 文档应描述背景、目标、非目标、设计方案、数据流、错误处理、测试策略和验收标准。Plan 文档应描述文件结构、任务拆分、测试命令和最终验证步骤。

## 提交说明

- 前端仓库提交目标为 `front`。
- 后端仓库提交目标为 `back`。
- 提交前确认只包含本次任务相关文档或代码，避免把未完成的本地改动混入提交。
