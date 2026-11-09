# CLAUDE.md

## 1. 技术栈与版本

| 技术 | 统一版本/选型 |
| --- | --- |
| 框架 | Vue 3（Composition API + `<script setup>`） |
| 构建工具 | Vite 5 |
| 语言 | TypeScript |
| 状态管理 | Pinia |
| UI 框架 | Element Plus |
| 路由 | Vue Router 4 |
| HTTP 客户端 | Axios |
| 测试框架 | Vitest + @vue/test-utils |
| 代码格式化 | Prettier |

- `package.json` 必须声明对应版本，禁止单独覆盖。
- 全局注册 Element Plus（`app.use(ElementPlus)`），中文语言包。
- 所有页面使用 `<script setup>` + Composition API，禁止 Options API。

## 2. 工程目录与分层

```
src/
├── main.ts                 # 应用入口
├── App.vue                 # 根组件
├── api/                    # API 层（按模块拆分）
│   ├── http.ts             # Axios 封装
│   ├── mode.ts             # Mock/真实 API 开关
│   ├── mock.ts             # Mock 数据层
│   ├── enterprise.ts       # 企业 API
│   ├── chain.ts            # 产业链 API
│   ├── tag.ts              # 标签 API
│   ├── matching.ts         # 智能挂链 API
│   └── dashboard.ts        # 数据看板 API
├── components/             # 通用组件
├── views/                  # 页面（按业务模块组织）
│   ├── auth/               # 登录
│   ├── enterprise/         # 企业管理
│   ├── chain/              # 产业链管理
│   ├── matching/           # 智能挂链
│   └── dashboard/          # 数据看板
├── router/                 # 路由配置
├── stores/                 # Pinia 状态管理
├── layouts/                # 布局组件
├── types/                  # TypeScript 类型定义
├── constants/              # 常量定义
├── styles/                 # 全局样式
└── test/                   # 测试配置
```

分层硬规则：
- `views/` 只负责页面展示和交互逻辑，禁止直接调用 Axios。
- `api/` 统一管理所有 HTTP 请求，每个模块一个文件。
- `stores/` 管理全局/模块状态，禁止在组件中直接修改 store state。
- `components/` 放可复用的通用组件，禁止包含业务逻辑。
- `types/` 统一定义 TypeScript 类型，禁止在组件中重复定义。

## 3. 命名规范

| 对象 | 规则 | 示例 |
|------|------|------|
| 组件文件名 | 大驼峰 | `ChainTree.vue` |
| 组件名 | 大驼峰，多词避免与 HTML 冲突 | `ChainTreeNode` |
| 变量/方法 | 小驼峰 | `loadTreeData` |
| 常量 | 全大写下划线 | `API_BASE_URL` |
| 路由 path | 小写短横线 | `/industry-chain/tree` |
| Pinia store | use + 名 + Store | `useChainStore` |
| props | 小驼峰 | `nodeId` |
| 事件 | 小驼峰，on 前缀 | `@node-click` |
| 类型/接口 | 大驼峰，I 前缀（可选） | `Enterprise` / `IEnterprise` |
| 枚举 | 大驼峰 | `EnterpriseStatus` |

## 4. 路由规范

- 使用 `createWebHistory` 模式。
- 所有后台页面嵌套在 `AdminLayout` 内。
- 路由懒加载：`() => import(...)`。
- 路由守卫：未登录重定向到 `/login`，已登录跳过登录页。
- 路由 name 唯一，path 小写短横线。

```typescript
// 路由定义示例
{
  path: '/enterprises',
  name: 'enterprises',
  component: () => import('@/views/enterprise/EnterpriseList.vue'),
  meta: { title: '企业管理' }
}
```

## 5. 组件规范

- 使用 `<script setup lang="ts">`。
- Props 用 `defineProps`，Emits 用 `defineEmits`。
- 组件名多词（如 `EnterpriseList` 而非 `List`）。
- 通用组件放 `src/components/`，业务组件放对应 `views/` 子目录。
- 组件文件结构：`<template>` → `<script setup>` → `<style scoped>`。

```vue
<script setup lang="ts">
// Props 定义
const props = defineProps<{
  nodeId: number
  title?: string
}>()

// Emits 定义
const emit = defineEmits<{
  (e: 'update', id: number): void
}>()
</script>
```

## 6. 状态管理（Pinia）

- 每个业务模块一个 Store。
- Store 文件命名：`use<模块>Store.ts`。
- 使用 Options API 风格（`defineStore` + state/actions）。
- 禁止在组件中直接修改 `$state`，必须通过 action。

```typescript
// Store 示例
export const useChainStore = defineStore('chain', {
  state: () => ({
    selectedChain: null as Chain | null,
    nodeTree: [] as ChainNode[]
  }),
  actions: {
    setSelectedChain(chain: Chain) {
      this.selectedChain = chain
    }
  }
})
```

## 7. API 层规范

- 所有 HTTP 请求封装在 `src/api/` 目录。
- 使用 `src/api/http.ts` 封装的 `get/post/put/remove` 方法。
- 禁止在组件中直接调用 `axios`。
- API 函数命名：`fetch<资源>`（查询）、`create<资源>`（新增）、`update<资源>`（修改）、`delete<资源>`（删除）。
- 响应自动解包 `{ code, message, data }` 格式。

```typescript
// API 函数示例
export async function fetchEnterprises(params: EnterpriseQuery): Promise<PageResult<Enterprise>> {
  return get('/enterprises', params)
}

export async function createEnterprise(data: CreateEnterpriseRequest): Promise<void> {
  return post('/enterprises', data)
}
```

### Mock 模式

- 环境变量 `VITE_API_MOCK` 控制，默认 `true`。
- 每个 API 函数内部检查 `isMockApiEnabled()`，启用则走 mock。
- Mock 层模拟真实异步延迟（180ms）。

## 8. HTTP 请求规范

- `baseURL`: `/api`
- 超时：10 秒
- 请求拦截器：自动注入 `Authorization: Bearer <token>`
- 响应拦截器：自动解包 `ApiResponse<T>`，code 非 200 抛出 `ApiBusinessError`
- 401 错误：自动清除 token，跳转登录页
- 其他错误：`ElMessage.error()` 统一提示

## 9. UI 框架使用

- Element Plus 全局注册，中文语言包。
- 常用组件：`el-table`、`el-pagination`、`el-form`、`el-input`、`el-select`、`el-dialog`、`el-message`、`el-tag`、`el-tree`。
- 自定义样式覆盖使用 `:deep()` 选择器。
- 响应式断点：1280px、980px、860px、720px。

## 10. TypeScript 规范

- 启用严格模式（`noUnusedLocals`、`noUnusedParameters`）。
- 类型定义统一放 `src/types/domain.ts`。
- 接口响应类型与后端文档对齐。
- 禁止使用 `any`，必须明确类型。

```typescript
// 类型定义示例
interface Enterprise {
  id: number
  name: string
  creditCode: string | null
  status: 'UNREGISTERED' | 'ACTIVE' | 'OUTBOUND'
  createdAt: string
  updatedAt: string
}
```

## 11. 测试规范

- 测试文件与源文件同目录，命名 `*.spec.ts`。
- 测试框架：Vitest + @vue/test-utils。
- 全局 API：`describe`、`it`、`expect`（通过 vitest/globals）。
- 测试命名：`should_<期望>_when_<条件>` 或中文描述。

```typescript
// 测试示例
describe('StatusTag', () => {
  it('should render active status with green color', () => {
    const wrapper = mount(StatusTag, { props: { status: 'ACTIVE' } })
    expect(wrapper.text()).toContain('存续')
  })
})
```

## 12. 代码格式

- 4 空格缩进，禁止 Tab。
- 单行 ≤ 120 字符。
- 使用 Prettier 格式化（`npm run format`）。
- 文件编码 UTF-8。
- 禁止未使用的 import、变量。

## 13. 编码约束（硬性）

- 禁止 `console.log`，使用 `ElMessage` 或统一日志工具。
- 禁止硬编码后端地址，使用 Vite 代理或环境变量。
- 禁止在模板中写复杂逻辑，提取为 computed 或方法。
- 禁止直接操作 DOM（除特殊场景），使用 Vue 响应式。
- 列表页必须有 loading、空状态、错误提示。
- 危险操作（删除、出库）必须有确认弹窗。
- 表单提交前做基础校验。

## 14. 环境配置

### Vite 配置

- 路径别名：`@` → `./src`
- 开发服务器端口：5173
- API 代理：`/api` → `http://localhost:8080`

### 环境变量

- `VITE_API_MOCK`：是否启用 Mock（默认 `true`）
- `VITE_API_BASE_URL`：API 基础路径（默认 `/api`）

### 构建

- chunk 大小警告阈值：1200KB
- 生产环境禁用 Mock

## 15. Git 提交规范

### commit 信息格式

```
<type>: <描述>
```

| type | 含义 |
|------|------|
| feat | 新功能 |
| fix | 修复 bug |
| docs | 文档变更 |
| style | 代码格式（不影响功能） |
| refactor | 重构 |
| test | 测试相关 |
| chore | 构建/依赖/配置 |

### 提交前自检

- [ ] 代码符合命名规范
- [ ] 无未使用的 import、变量
- [ ] 无硬编码的地址、密码
- [ ] 接口字段与后端文档一致
- [ ] 列表页有 loading、空状态、错误提示
- [ ] 危险操作有确认弹窗
