# 产业链管理系统后端

基于 Spring Boot 3.5.15 / JDK 21 / MyBatis-Plus 3.5.7 / MySQL 5.7 / Spring Security + JWT 的后端基础工程。
严格遵循 `CLAUDE.md` 与《后端基础工程统一规范.md》。

## 环境要求

| 组件 | 版本 |
| --- | --- |
| JDK | 21 (LTS) |
| Maven | 3.8+ |
| MySQL | 5.7 |
| Node/前端 | 联调地址默认 `http://localhost:5173` |

## 目录结构

```
git.ggwzz
├── IndustryChainApplication.java        启动类
├── common                              公共：统一响应、分页、异常、配置、工具、操作日志 AOP
│   ├── api                             Result / PageResult / ResultCode
│   ├── config                          MyBatis-Plus / Web 配置
│   ├── exception                       业务异常与全局异常处理
│   ├── log                             @OperationLog 与切面
│   └── util                            通用工具
├── security                            Spring Security + JWT 鉴权
│   ├── config                          安全配置
│   ├── filter                          JWT 认证过滤器
│   ├── handler                         401 / 403 统一处理
│   ├── service                         登录用户加载与安全用户模型
│   └── token                           JWT 配置与生成解析
├── auth                                鉴权业务（登录/登出/当前用户/健康检查 + RBAC）
│   ├── controller / service / service.impl
│   ├── mapper / entity / dto / vo / enums
│   └── resources/mapper/auth           用户、角色、权限 XML
├── chain                               产业链业务
│   ├── controller                      产业链、节点、挂链接口
│   ├── service / service.impl          产业链 CRUD、节点树、移动排序、挂链关系
│   ├── mapper / entity / dto / vo / enums
│   └── dto                             创建/更新/移动/排序/挂链请求
├── matching                            智能匹配业务
│   ├── controller / service / service.impl
│   └── mapper / entity / dto / vo / enums
├── operationlog                        操作日志查询与落库
│   └── controller / service / service.impl / mapper / entity / dto / vo
├── enterprise / tag                    企业与标签模块骨架，供后续业务迭代
└── resources
    ├── application*.yml                多环境配置
    ├── logback-spring.xml              日志
    ├── mapper/**.xml                   MyBatis XML
    ├── smart-doc.json                  接口文档配置
    └── db/
        ├── migration                   V1 鉴权表、V2 业务核心表、V3 chain 树结构迁移
        └── data                        演示账号、业务演示数据、chain 权限数据
src/test
├── java/git/ggwzz                      auth / chain / common / matching / security 测试
└── resources
    ├── application-unittest.properties H2 单元测试配置
    ├── schema.sql / data.sql           测试库结构与数据
    └── http/chain.http                 chain 接口联调脚本
```

## 环境变量

启动前请准备以下环境变量（参考 `.env.example`，复制为 `.env` 后导入终端）：

| 变量 | 说明 | dev 默认 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 环境：dev/test/prod | dev |
| `DB_URL` | 数据源 URL | localhost:3306/industry_chain |
| `DB_USERNAME` | 数据库用户 | root |
| `DB_PASSWORD` | 数据库密码 | root |
| `JWT_SECRET` | JWT 密钥（≥32 字节，生产必须高强度） | dev 弱默认 |
| `SERVER_PORT` | 服务端口 | 8080 |
| `CORS_ORIGIN` | 前端地址（test/prod 必填） | — |

> 也可将真实值写入 `examples/application-local.yml.example` 拷贝出的 `application-local.yml`（已被 `.gitignore` 忽略）。
> 禁止将真实密钥/密码提交到 Git。

## 数据库初始化

业务表由项目方自行维护；本工程仅负责鉴权与操作日志表。按顺序执行：

1. （项目方）业务表脚本：enterprise / tag / chain / matching 等业务表
2. 本工程鉴权表：`src/main/resources/db/migration/V1__sys_auth.sql`
3. 演示数据：`src/main/resources/db/data/V1__demo_admin.sql`

## 本地启动

```bash
# 方式一：连远程库（推荐，配置已写入 application-local.yml，不进 Git）
mvn spring-boot:run -Dspring-boot.run.profiles=local
# 或打包后运行
java -jar -Dspring.profiles.active=local target/industry-chain-1.0.0.jar

# 方式二：连本地库，需先设置环境变量
export SPRING_PROFILES_ACTIVE=dev
export JWT_SECRET=your-local-strong-random-secret-at-least-32-bytes-long
mvn spring-boot:run
```

`application-local.yml` 已在 `.gitignore` 中，含远程库连接（165.99.43.66:3030 / industry_platform）
与 JWT 密钥，请勿提交。

## 联调清单

```bash
#登录（windows环境）
curl.exe -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
# 登录（admin / admin123）
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
# => { "code":200, "data":{ "token":"...", "tokenType":"Bearer", ... } }

# 当前用户（带 Bearer）
curl http://localhost:8080/api/auth/me \
  -H 'Authorization: Bearer <token>'
# => { "code":200, "data":{ "username":"admin", "permissions":[...] } }

# 健康检查（匿名）
curl http://localhost:8080/api/health
# => { "code":200, "data":{ "status":"up" } }

# 不带 Token 访问受保护接口 => 401（统一 JSON）
curl http://localhost:8080/api/auth/me
```

## 接口文档（smart-doc）

```bash
mvn smart-doc:html
# 生成位置：src/main/resources/static/doc/index.html
```

生产环境不部署文档生成能力。

## 测试与构建

```bash
mvn test        # 单元测试 + 集成测试（H2 内存库，无需真实 MySQL）
mvn package     # 打包
```

测试覆盖：
- `ResultTest` / `PageResult` 统一响应
- `JwtTokenProviderTest` JWT 生成/解析/过期
- `AuthServiceTest` 登录正确流程 + 不存在/停用/锁定/密码错误
- `AuthControllerTest` MockMvc 状态码与响应结构
- `SecurityFlowTest` 匿名/登录成功/Token 缺失/Token 失效/已认证访问

## 演示账号

| 用户名 | 密码 | 说明 |
| --- | --- | --- |
| admin | admin123 | 超级管理员，拥有全部演示权限 |

> 仅为联调用，生产环境请删除并改用强密码。

## 关键约定速查

- 统一响应 `{ code, message, data }`，成功码 200；HTTP 状态码与 body code 一致。
- 分页请求 `pageNum/pageSize`，响应 `records/total/pageNum/pageSize`。
- 鉴权头 `Authorization: Bearer <token>`，JWT 有效期 2 小时。
- 401 由 `AuthenticationEntryPointImpl` 统一返回；403 由 `AccessDeniedHandlerImpl` 统一返回。
- 权限编码 `模块:资源:动作`，以数据库为准，方法级用 `@PreAuthorize` 控制。
- 密码 BCrypt（`{bcrypt}` 前缀），登录失败统一提示"用户名或密码错误"。
- 关键操作（登录/登出等）通过 `@OperationLog` 注解 + AOP 异步落库。
