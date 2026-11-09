# 产业链管理系统

产业链管理系统是一个前后端分离的企业与产业链协同管理平台。项目正式代码由 `front` 前端工程和 `back` 后端工程组成，覆盖企业库管理、产业链节点维护、企业挂链、智能匹配、AI 分析、分析报告、数据看板、系统权限和操作日志等业务场景。

## 项目概述

### 功能介绍

前端 `front` 提供后台管理界面，主要功能包括：

- 登录、鉴权状态维护和后台布局。
- 企业管理、企业详情、企业导入记录和标签管理。
- 产业链管理、节点树展示、节点移动和同级排序。
- 智能挂链工作台，支持批量任务、单企业分析、候选结果采纳、拒绝和人工确认。
- 分析报告列表、报告详情、产业链诊断和报告导出交互。
- 数据看板、系统管理、操作日志和 AI 助手面板。

后端 `back` 提供 REST API、鉴权、业务规则和数据持久化能力，主要功能包括：

- Spring Security + JWT 登录认证、RBAC 权限控制和当前用户信息查询。
- 企业、标签、产业链、节点和企业节点关系管理。
- 智能挂链任务、批量挂链任务、AI 对话分析、匹配评测和推荐缓存。
- 分析报告、产业链健康诊断、Dashboard 汇总和报告导出。
- 系统用户、角色、权限、操作日志查询与维护。
- 统一响应、统一异常、参数校验、事务和操作审计。

### 技术栈

前端技术栈：

- Vue 3，Composition API，`<script setup>`。
- TypeScript、Vite 5、Vue Router 4、Pinia。
- Element Plus、Axios、ECharts、AntV G6。
- Vitest、jsdom、`@vue/test-utils`。
- Prettier。

后端技术栈：

- JDK 21、Spring Boot 3.5.15、Maven 3.8+。
- MyBatis-Plus 3.5.7、MySQL 5.7、HikariCP。
- Spring Security + JWT、Jakarta Validation、Lombok。
- SLF4J + Logback、Spring AOP、smart-doc。
- JUnit 5、Mockito、Spring Boot Test、MockMvc、Spring Security Test。
- 测试环境使用 H2 内存数据库。

### 环境要求

前端环境：

- Node.js 20 或兼容当前依赖的 LTS 版本。
- npm，依赖版本以 [front/package-lock.json](front/package-lock.json) 为准。
- 开发服务器默认端口为 `5173`。
- `/api` 默认由 Vite 代理到 `http://localhost:8080`。
- 默认联调真实后端；如需本地 Mock，可设置 `VITE_API_MOCK=true`。

后端环境：

- JDK 21。
- Maven 3.8 或更高版本。
- MySQL 5.7。
- 服务默认端口为 `8080`。
- 默认允许前端联调来源 `http://localhost:5173`。
- 数据库密码、JWT 密钥、外部模型 API Key 等敏感配置必须通过环境变量或不入库配置提供。

常用后端环境变量包括：

| 变量 | 说明 | 默认或用途 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Spring 运行环境 | 默认 `dev` |
| `SERVER_PORT` | 后端服务端口 | 默认 `8080` |
| `DB_URL` | 数据库连接地址 | 本地或部署环境提供 |
| `DB_USERNAME` | 数据库用户名 | 本地或部署环境提供 |
| `DB_PASSWORD` | 数据库密码 | 不允许提交到 Git |
| `JWT_SECRET` | JWT 签名密钥 | 不允许提交到 Git |
| `LLM_API_KEY` / `MATCHING_LLM_API_KEY` | AI 分析或匹配模型 Key | 不允许提交到 Git |

## 项目结构

```text
.
├── front/                         # Vue 3 前端工程
│   ├── src/api/                   # API 封装与 Mock 开关
│   ├── src/components/            # 通用组件
│   ├── src/constants/             # 常量定义
│   ├── src/domain/                # 前端领域规则与 ViewModel 辅助逻辑
│   ├── src/layouts/               # 后台布局
│   ├── src/router/                # Vue Router 路由
│   ├── src/stores/                # Pinia 状态管理
│   ├── src/styles/                # 全局样式
│   ├── src/test/                  # 测试配置
│   ├── src/types/                 # TypeScript 类型定义
│   ├── src/views/                 # 页面组件，按业务模块组织
│   └── public/                    # 静态资源
└── back/                          # Spring Boot 后端工程
    ├── src/main/java/git/ggwzz/   # 后端根包
    ├── src/main/resources/        # 配置、Mapper XML、数据库脚本和日志配置
    └── src/test/                  # 后端自动化测试与测试资源
```

## 构建与测试命令

### 前端命令

在 `front` 目录执行：

```bash
cd front
npm install
```

启动开发服务器：

```bash
npm run dev
```

临时启用本地 Mock：

```bash
VITE_API_MOCK=true npm run dev
```

运行测试、构建和格式检查：

```bash
npm test
npm run build
npm run format:check
```

命令说明：

- `npm run dev`：启动 Vite 开发服务器，默认端口 `5173`。
- `npm test`：运行 Vitest 单元测试和组件测试。
- `npm run build`：执行 `vue-tsc -b` 类型检查并生成生产构建。
- `npm run preview`：预览生产构建结果。
- `npm run format:check`：检查 Prettier 格式。
- `npm run format`：格式化前端文件。

### 后端命令

在 `back` 目录执行：

```bash
cd back
mvn test
mvn package
```

本地启动服务：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

打包后运行：

```bash
java -jar -Dspring.profiles.active=local target/industry-chain-1.0.0.jar
```

生成接口文档：

```bash
mvn smart-doc:html
```

命令说明：

- `mvn test`：运行后端单元测试、MockMvc 测试和基于 H2 的集成测试。
- `mvn package`：执行测试并打包 Spring Boot 应用。
- `mvn spring-boot:run -Dspring-boot.run.profiles=local`：使用本地 profile 启动后端服务。
- `mvn smart-doc:html`：根据 JavaDoc 和 smart-doc 配置生成接口文档。

### 推荐联调顺序

1. 启动 MySQL 5.7，并准备 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET` 等后端配置。
2. 在 `back` 目录运行 `mvn spring-boot:run -Dspring-boot.run.profiles=local`。
3. 在 `front` 目录运行 `npm run dev`。
4. 访问 Vite 输出的本地地址，通过 `/api` 代理访问后端。

## 代码风格指南

### 前端代码要求

目录与分层：

- 页面组件放在 `front/src/views/`，按业务模块分目录。
- 通用组件放在 `front/src/components/`，不得耦合具体业务状态流转。
- API 请求统一通过 `front/src/api/` 封装，组件不得直接调用 Axios。
- 全局或跨页面状态放在 `front/src/stores/`，组件通过 action 修改状态。
- 类型定义集中在 `front/src/types/domain.ts`，接口字段需要与后端响应保持一致。
- 路由集中在 `front/src/router/index.ts`，后台页面挂载在 `AdminLayout` 下。
- 全局样式放在 `front/src/styles/`，公共静态资源放在 `front/public/`。

命名与组件规范：

- 组件文件名使用大驼峰，例如 `EnterpriseList.vue`。
- 变量和函数使用小驼峰，常量使用全大写下划线。
- 路由 path 使用小写短横线，路由 name 保持唯一。
- Pinia Store 使用 `use<模块>Store` 命名。
- Vue 文件结构保持为 `<template>`、`<script setup lang="ts">`、`<style scoped>`。
- 所有页面使用 Composition API 和 `<script setup>`，禁止 Options API。
- 禁止在组件中直接操作 DOM，复杂模板逻辑应提取为 computed 或方法。

接口与交互规范：

- HTTP `baseURL` 为 `/api`，请求拦截器自动注入 `Authorization: Bearer <token>`。
- 响应按 `{ code, message, data }` 解包，`code` 非 `200` 时抛出业务错误并提示。
- 401 统一清除登录态并跳转登录页。
- 列表页必须包含 loading、空状态和错误反馈。
- 删除、出库、撤回等风险操作必须二次确认。
- 表单提交前必须做基础校验。

格式要求：

- 禁止保留调试 `console.log`。
- 禁止硬编码后端地址，使用 Vite 代理或环境变量。
- 禁止未使用的 import 和变量。
- 单行长度控制在 120 字符以内，文件编码 UTF-8。
- 项目使用 Prettier 格式化，提交前运行 `npm run format:check`。

### 后端代码要求

目录与分层：

- 根包固定为 `git.ggwzz`。
- 业务模块按 `controller / service / service.impl / mapper / entity / dto / vo` 分层。
- `common` 放统一响应、分页、异常、配置、日志和工具。
- `security` 放安全配置、JWT 过滤器、401/403 处理、用户加载和 Token 能力。
- 配置文件放在 `back/src/main/resources/application*.yml`。
- MyBatis XML 放在 `back/src/main/resources/mapper/`。
- 数据库迁移脚本放在 `back/src/main/resources/db/migration/`。
- 演示或初始化数据放在 `back/src/main/resources/db/data/`。
- 日志配置放在 `back/src/main/resources/logback-spring.xml`。

分层规则：

- Controller 只负责接收参数、校验、调用 Service 和返回统一响应，禁止直接调用 Mapper。
- Service 负责业务规则、状态流转和事务，不返回 `Result`。
- Mapper 只负责数据访问，不写业务判断。
- Entity 只对应数据库表，接口响应使用 VO，请求参数使用 DTO。
- 超过两个业务参数时使用 DTO 或 Query 对象，禁止用 `Map` 承载正式接口参数。
- 统一使用构造器注入，禁止字段注入。

接口风格：

- JSON 接口统一返回 `{ code, message, data }`，成功码固定为 `200`。
- 分页请求使用 `pageNum/pageSize`，响应使用 `records/total/pageNum/pageSize`。
- REST Controller 使用 `@RestController`。
- GET 用于查询，POST 用于新增或业务动作，PUT 用于修改，DELETE 用于删除。
- 文件上传使用 `multipart/form-data`，参数名统一为 `file`。
- 文件下载直接写 `HttpServletResponse`，不套统一响应。
- Controller、DTO、VO 和公共响应对象需要编写 JavaDoc，接口变更后同步生成 smart-doc 文档。

数据库与 SQL：

- MySQL 版本固定为 5.7，存储引擎使用 InnoDB，字符集使用 `utf8mb4`。
- 表名和字段名使用小写下划线，表名使用单数。
- 删除一律逻辑删除，不建立数据库外键，不使用存储过程。
- 金额、税收、产值和置信度使用 `DECIMAL`，禁止使用 `FLOAT` 或 `DOUBLE`。
- SQL 参数必须使用 `#{}`，禁止用 `${}` 拼接用户输入。
- 禁止 `SELECT *`，多表查询字段需要带表别名。
- 多表写入、关键状态流转和采纳推荐等操作必须使用 `@Transactional(rollbackFor = Exception.class)`。

鉴权、日志与配置：

- 请求头统一使用 `Authorization: Bearer <token>`，禁止自定义 `X-Token`。
- JWT 有效期为 2 小时，密钥通过环境变量 `${JWT_SECRET}` 提供。
- 401 由认证入口统一返回，403 由权限拒绝处理器统一返回。
- 权限编码使用 `模块:资源:动作`，例如 `enterprise:enterprise:query`。
- 日志使用 SLF4J 占位符 `{}`，禁止 `System.out.println`、`System.err.println` 和 `printStackTrace()`。
- 禁止返回或记录 SQL、堆栈、服务器路径、完整 Token、密码、JWT 密钥和数据库密码等敏感信息。
- 按环境拆分 `application.yml`、`application-dev.yml`、`application-test.yml` 和 `application-prod.yml`。

命名与格式：

- Java 类名使用 UpperCamelCase。
- 方法和变量使用 lowerCamelCase。
- 常量使用全大写下划线。
- 包名全部小写。
- 使用 4 个空格缩进，禁止 Tab。
- 单行长度控制在 120 字符以内，文件编码 UTF-8。
- 禁止魔法值，状态值使用枚举或常量。
- `BigDecimal` 使用字符串构造或 `BigDecimal.valueOf()`。

## 测试说明

### 前端测试

前端使用 Vitest、jsdom 和 `@vue/test-utils`。测试文件与被测文件同目录，命名为 `*.spec.ts`，全局测试 API 通过 `vitest/globals` 提供。

前端测试验收标准：

- API 封装测试覆盖请求路径、请求方法、参数和响应映射。
- ViewModel 或领域规则测试覆盖任务状态、候选结果、风险提示和操作日志文本。
- 页面组件测试覆盖关键文案、按钮状态、空状态和主要用户操作。
- 路由和 Store 测试覆盖权限跳转、菜单状态和跨页面状态。
- 涉及 UI 交互时，需验证 loading、空状态、错误反馈和危险操作确认弹窗。
- 提交前至少运行 `npm test`。
- 涉及路由、类型、构建配置或公共类型时，同时运行 `npm run build`。
- 提交前建议运行 `npm run format:check`。

### 后端测试

后端测试位于 `back/src/test/java`，类名使用 `被测类名 + Test`。测试框架包括 JUnit 5、Mockito、Spring Boot Test、MockMvc 和 Spring Security Test。数据库相关测试使用 H2 与 `back/src/test/resources` 中的测试资源，保持自动化、独立、可重复。

后端测试验收标准：

- Service 单测使用 Mockito 隔离 Mapper、外部模型和异步执行器。
- Controller 测试使用 MockMvc 验证权限、状态码、参数校验和统一响应格式。
- Security 测试覆盖匿名访问、登录成功、Token 缺失、Token 失效和无权限访问。
- Mapper、迁移脚本或数据库统计相关逻辑使用 H2 测试验证字段、数据和边界场景。
- 核心业务覆盖正确流程、边界值和异常流程。
- 禁止通过控制台输出人工判断测试结果。
- 提交前至少运行 `mvn test`。
- 涉及打包、资源、迁移脚本、依赖或启动配置时，同时运行 `mvn package`。

## 提交前自检

- 前端改动已运行 `npm test`，必要时运行 `npm run build` 和 `npm run format:check`。
- 后端改动已运行 `mvn test`，必要时运行 `mvn package`。
- 接口字段、统一响应、分页格式和权限约定与前后端文档一致。
- 未提交数据库真实密码、JWT 密钥、外部模型 API Key、个人绝对路径或共享服务器凭据。
- 只提交与本次任务相关的 `front`、`back` 或项目文档改动，避免混入本地临时文件。
