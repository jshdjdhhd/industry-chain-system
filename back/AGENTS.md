# CLAUDE.md
## 1. 技术栈与版本

| 技术 | 统一版本/选型 |
| --- | --- |
| JDK | 21 (LTS) |
| Spring Boot | 3.5.15 |
| Maven | 3.8+ |
| MyBatis-Plus | 3.5.7 |
| MySQL | 5.7 |
| 连接池 | HikariCP (Spring Boot 默认) |
| 参数校验 | spring-boot-starter-validation (Jakarta Validation) |
| 鉴权 | Spring Security + JWT |
| 密码 | Spring Security DelegatingPasswordEncoder (BCrypt, `{bcrypt}` 前缀) |
| 日志 | SLF4J + Logback |
| 测试 | JUnit 5 + Mockito + Spring Boot Test |
| 接口文档 | smart-doc |

- `pom.xml` 必须声明 `<java.version>21</java.version>` 与 `<mybatis-plus.version>3.5.7</mybatis-plus.version>`。
- MySQL 驱动、Lombok、Spring Security、Validation、测试依赖走 Spring Boot 依赖管理，禁止单独覆盖版本。
- JWT 实现库版本必须在父 `pom.xml` 统一，业务模块不得各自引入。

## 2. 工程目录与分层

根包固定为 `git.ggwzz`。

```text
git.ggwzz
├── IndustryChainApplication.java
├── common
│   ├── api       (Result, PageResult, ResultCode)
│   ├── exception (BusinessException, GlobalExceptionHandler)
│   ├── config     (MybatisPlusConfig, WebConfig)
│   ├── log
│   └── util
├── security (config / filter / handler / service / token)
├── auth
├── enterprise
├── tag
├── chain
├── matching
└── operationlog
```

每个业务模块内部按职责拆分：`controller / service / service.impl / mapper / entity / dto / vo`。

分层硬规则：
- Controller 只做：接收参数、校验、调用 Service、返回响应；禁止直接调用 Mapper。
- Service 负责业务规则、状态流转、事务；**不返回 `Result`**。
- Mapper 只做数据访问，不写业务判断。
- Entity 与表对应，**禁止直接作为接口响应**；接口只返回 VO。
- DTO 接收请求，VO 返回页面数据。
- 超过两个业务参数必须用 DTO/Query 对象，禁止用 `Map` 传递。
- 统一构造器注入，禁止字段注入。

## 3. 统一响应

所有 JSON 接口返回 `{ "code", "message", "data" }` 结构，对应 `Result<T>`。
- 成功码固定 `200`，HTTP 状态码与响应体 `code` 一致。
- 禁止同时存在 `0` 与 `200` 两种成功码。
- 统一通过 `Result.success()` / `Result.failure()` 生成，禁止手工拼装。
- 文件下载直接写 `HttpServletResponse`，不套 `Result`。
- 业务异常信息必须用户可理解，禁止把 SQL、堆栈、服务器路径返回前端。

错误码：200 成功 / 400 参数或一般业务规则不允许 / 401 未登录或 Token 缺失/无效/过期 / 403 已认证无权限 / 404 资源不存在 / 409 唯一冲突或重复绑定 / 500 未预期系统错误。

## 4. 分页

- 请求参数：`pageNum`（从 1 开始，默认 1）、`pageSize`（默认 10，最大 100）。**禁止混用 `page`/`size` 与 `pageNum`/`pageSize`。**
- 响应 `data`：`{ records, total, pageNum, pageSize }`。**禁止用 `list` 替代 `records`。**
- 由 MyBatis-Plus `PaginationInnerInterceptor` 实现；total 为 0 时直接返回空分页。

## 5. 参数校验与异常

- 请求 DTO 用 Jakarta Validation（如 `@NotBlank`），消息用中文。
- Controller 参数必须加 `@Valid` / `@Validated`。
- `GlobalExceptionHandler` 至少覆盖：`MethodArgumentNotValidException`、`ConstraintViolationException`、`HttpMessageNotReadableException`、`BusinessException`、`AccessDeniedException`、未知 `Exception`。
- 可预检查避免的异常不得用 `try-catch` 做流程控制；禁止吞异常。
- 事务方法捕获后若仍需回滚，必须重新抛出或显式回滚。
- 未知异常记录完整堆栈，对外返回 500 通用提示。
- 禁止在 `finally` 中返回结果。

## 6. 数据库（MySQL 5.7）

- 存储引擎 InnoDB；字符集 `utf8mb4`；排序 `utf8mb4_general_ci`。
- **禁止** MySQL 8 专属语法（递归 CTE、窗口函数、`JSON_TABLE`）；树形数据用 `parent_id` + `node_path` + 后端组装。
- 公共字段统一：`id BIGINT UNSIGNED AUTO_INCREMENT`、`created_at`、`updated_at ON UPDATE CURRENT_TIMESTAMP`、`deleted TINYINT`。
- Java Entity 逻辑删除属性命名为 `deleted`，用 `@TableLogic @TableField("deleted")` 映射。
- 表名/字段名小写下划线，表名单数；表与字段必须 `COMMENT`。
- 金额、税收、产值、置信度必须用 `DECIMAL`，禁止 `FLOAT/DOUBLE`。
- 索引命名：主键 `pk_字段`，唯一 `uk_字段`，普通 `idx_字段`；唯一字段必须建唯一索引，关联/高频字段建普通索引。
- 不建数据库外键，不做级联更新/删除，引用完整性由 Service 保证；不用存储过程；删除一律逻辑删除。

SQL 规则：
- 参数一律 `#{}`，禁止 `${}` 拼接用户输入。
- 禁止 `SELECT *`，明确列出字段；多表查询字段带表别名。
- 单条 SQL 原则不超过 3 个表 JOIN。
- 统计用 `COUNT(*)`，求和用 `IFNULL(SUM(col), 0)`。
- 页面搜索禁止左模糊；确需包含搜索须评估数据量。
- `IN` 集合 ≤ 1000；更新记录必须同步 `updated_at`。

## 7. MyBatis / MyBatis-Plus

- 单表 CRUD 用 `BaseMapper` / `IService`；条件查询优先 `LambdaQueryWrapper` / `LambdaUpdateWrapper`；分页用 MP 分页插件；逻辑删除用 `@TableLogic`。
- 时间字段：自动填充或数据库默认值二选一，**全项目只能统一一种**。
- 多表、树形统计、复杂动态查询用 Mapper XML；`namespace` 必须是 Mapper 接口全限定名，`id` 必须与 Mapper 方法名一致。
- 复杂结果必须定义 `resultMap`，禁止用 `HashMap` 接收业务结果。
- 不用 ActiveRecord，不默认开启二级缓存。
- 配置：`map-underscore-to-camel-case: true`；`logic-delete-field: deleted`，`logic-delete-value: 1`，`logic-not-delete-value: 0`。
- 生产配置禁止控制台 SQL 输出（开发环境可按需开启）。

## 8. 鉴权（Spring Security + JWT + MySQL）

- 无状态会话：`SessionCreationPolicy.STATELESS`；第一版不强制 Redis。
- **禁止**课件中“任意非空 X-Token 即视为 admin”的演示逻辑。
- Token 至少含：用户 ID、用户名、签发时间、过期时间；权限以数据库当前状态为准，不在代码写死。
- 请求头统一 `Authorization: Bearer <token>`，**禁止自定义 `X-Token`**。
- JWT 有效期 2 小时；密钥通过环境变量 `${JWT_SECRET}`、`expiration: 7200`。密钥/数据库密码等敏感信息禁止提交 Git。

鉴权接口：`POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/me`、`GET /api/health`。
匿名放行：`/api/auth/login`、`/api/health`、smart-doc 静态文档资源（仅开发环境）；其余 `/api/**` 默认要求认证。

密码与状态：
- 数据库只存 BCrypt；用 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`，格式 `{bcrypt}` 前缀。
- 登录检查：存在/删除/停用/锁定/账号过期；失败统一提示“用户名或密码错误”，不泄露账号是否存在。

授权：
- 开启 `@EnableMethodSecurity`，用 `@PreAuthorize` 做权限控制。
- 权限编码格式 `模块:资源:动作`，如 `enterprise:enterprise:query`、`chain:node:update`、`matching:task:execute`。
- 401 由 `AuthenticationEntryPoint` 统一返回，403 由 `AccessDeniedHandler` 统一返回。
- 数据级权限由后端保证，禁止仅靠前端隐藏按钮。
- JWT 过滤器继承 `OncePerRequestFilter`，校验成功后才写 `SecurityContext`，置于 `UsernamePasswordAuthenticationFilter` 之前；Bearer 模式关闭表单登录与 HTTP Basic；无状态 Bearer 下关闭 CSRF；CORS 只允许配置中的前端地址，禁止通配。

## 9. 日志与操作审计

- 用 `logback-spring.xml` 配置；代码用 SLF4J，可用 Lombok `@Slf4j`。
- 禁止 `System.out/err.println`、`printStackTrace()`。
- 占位符 `{}`，禁止字符串拼接；异常日志记录业务上下文 + 完整堆栈。
- 禁止记录密码、完整 Token、JWT 密钥、数据库密码；敏感信息脱敏。
- 强制记录操作日志：登录、企业入库/出库、节点变更、挂链/解绑、推荐采纳/拒绝；建议用注解 + AOP 统一实现。

## 10. 接口与文件 / 接口文档

- `@RestController`；GET 查、POST 增、PUT 改、DELETE 删；业务动作用 POST（如 `/api/enterprises/{id}/inbound`）。
- 上传 `multipart/form-data`，参数名统一 `file`；必须校验扩展名、MIME、大小。
- 文件路径用 `Path` / `File.separator`，禁止硬编码 OS 分隔符；下载必须设 Content-Type 与 Content-Disposition。
- smart-doc 据 JavaDoc 生成；Controller/DTO/VO/公共响应对象必须写 JavaDoc；接口变更同步更新 JavaDoc 并重新生成；生产环境不部署文档生成能力。

## 11. 配置管理

按环境拆分：`application.yml`（只放公共配置）、`application-dev.yml`、`application-test.yml`、`application-prod.yml`；通过 `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` 激活。
**禁止进入仓库**：数据库真实密码、JWT 密钥、外部模型 API Key、个人绝对路径、共享服务器凭据 → 一律放环境变量或不提交 Git 的本地配置。

## 12. 事务

以下操作必须在 Service 层用 `@Transactional(rollbackFor = Exception.class)`：
企业批量导入/出库、企业-标签绑定或解绑并记日志、节点新增后维护 `node_path`、节点删除校验与逻辑删除、企业挂链与解绑、采纳推荐（更新推荐结果 + 创建挂链关系 + 写操作日志）。
事务方法必须是可被 Spring 代理的 `public` 方法；禁止同类内通过 `this` 调用事务方法。

## 13. 测试

- 测试位于 `src/test/java`；类名 `被测类名 + Test`；JUnit 5 断言，禁止靠控制台输出人工判断；自动化、独立、可重复。
- Service 单测用 Mockito 隔离 Mapper 等外部依赖；Controller 用 MockMvc 验证状态码与统一响应格式。
- Security 测试至少覆盖：匿名访问、登录成功、Token 缺失、Token 失效、无权限访问。
- 核心业务覆盖正确流程 + 边界值 + 异常流程。
- 提交前统一执行 `mvn test` 与 `mvn package`。

## 14. 编码约束（硬性）

- 类名 UpperCamelCase，方法/变量 lowerCamelCase，常量大写下划线，包名全小写。
- 4 空格缩进，禁止 Tab；单行 ≤ 120 字符。
- 禁止魔法值，状态用枚举或常量；`BigDecimal` 用字符串构造或 `BigDecimal.valueOf()`。
- 禁止返回 Entity（只返回 VO）；禁止 Controller 直接调 Mapper；禁止 Service 返回 `Result`。
- 禁止“大而全”的通用更新接口修改所有字段；多表写入必须事务；关键接口必须有参数校验、异常处理、日志。
