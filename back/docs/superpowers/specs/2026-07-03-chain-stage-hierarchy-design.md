# 产业链上下游阶段层级设计

## 1. 背景

当前系统已经支持基于 `parent_id`、`node_level` 和 `node_path` 的任意深度节点树，也支持节点新增、编辑、移动、排序和删除。但现有演示及种子数据多数仍将细分节点直接放在产业链根下，使用注释、名称或错误复用的 `node_level` 表达上游、中游、下游，未形成以下真实层级：

```text
产业链
├── 上游
│   └── 细分节点
├── 中游
│   └── 细分节点
└── 下游
    └── 细分节点
```

本设计在保留现有通用树接口的前提下，为每条新建产业链自动生成三个阶段节点，并将项目中可以确定归属的现有演示数据迁移到对应阶段下。

## 2. 目标

1. 新建产业链时自动生成上游、中游、下游三个一级阶段节点。
2. 阶段节点具有与名称无关的稳定类型标识。
3. 阶段节点允许改名和同级排序，但禁止移动和单独删除。
4. 普通节点可以创建或移动到阶段节点下，并继续形成任意深度子树。
5. 前端明确展示阶段类型并提前禁用非法操作。
6. 迁移项目内能够确定归属的演示及种子数据，不猜测未知业务数据。
7. 保持现有产业链和节点接口路径不变。

## 3. 非目标

- 不为未知历史产业链自动推断节点所属阶段。
- 不新增独立的阶段管理页面。
- 不引入 `chain_stage` 独立表。
- 不改变企业与细分节点的挂接关系。
- 不禁止管理员修改阶段节点的显示名称。
- 不禁止删除整条产业链。

## 4. 数据模型

### 4.1 阶段类型

后端新增枚举 `ChainStageType`：

```java
public enum ChainStageType {
    UPSTREAM,
    MIDSTREAM,
    DOWNSTREAM
}
```

前端新增联合类型：

```typescript
export type ChainStageType =
    | "UPSTREAM"
    | "MIDSTREAM"
    | "DOWNSTREAM";
```

### 4.2 表字段

`chain_node` 新增可空字段：

```sql
`stage_type` VARCHAR(16) NULL COMMENT '产业链阶段：UPSTREAM/MIDSTREAM/DOWNSTREAM，NULL 为普通节点'
```

普通节点的 `stage_type` 为 `NULL`。阶段节点的值固定为三个枚举值之一。

增加唯一索引：

```sql
UNIQUE KEY `uk_chain_node_stage` (`chain_id`, `stage_type`, `is_deleted`)
```

MySQL 允许唯一索引中存在多个 `NULL`，因此该索引不会限制普通节点数量，但会限制同一产业链下同一状态的阶段节点只能有一个。

### 4.3 树形不变量

阶段节点必须满足：

- `parent_id IS NULL`
- `node_level = 1`
- `node_path = CONCAT('/', id, '/')`
- 每条产业链最多存在一个 `UPSTREAM`
- 每条产业链最多存在一个 `MIDSTREAM`
- 每条产业链最多存在一个 `DOWNSTREAM`

普通节点不受阶段类型限制，可以位于阶段节点或其他普通节点下。

## 5. 后端设计

### 5.1 新建产业链

`ChainServiceImpl.createChain()` 使用 `@Transactional(rollbackFor = Exception.class)`。

处理顺序：

1. 防御性校验请求和产业链名称。
2. 插入产业链并确认生成的产业链 ID 非空。
3. 按固定顺序创建阶段节点：
   - `UPSTREAM`：默认名称“上游”，`sort_order=1`
   - `MIDSTREAM`：默认名称“中游”，`sort_order=2`
   - `DOWNSTREAM`：默认名称“下游”，`sort_order=3`
4. 每个阶段节点插入后回填规范 `node_path`。
5. 任一步骤失败时抛出异常，由事务回滚产业链和已经插入的阶段节点。

阶段节点创建逻辑放在 `ChainServiceImpl` 内部的专用私有方法中，不通过公开普通节点创建 DTO 接收 `stageType`。

### 5.2 更新节点

`UpdateNodeRequest` 不增加 `stageType`。

阶段节点允许更新：

- `nodeName`
- `keywords`
- `description`
- `sortOrder`

更新过程不得修改或清空数据库中的 `stage_type`。

### 5.3 移动节点

`moveNode()` 读取源节点后优先检查 `stageType`。

如果 `stageType != null`，无论 `newParentId` 是否为空，均返回 HTTP 400：

```text
上下游阶段节点不允许移动
```

普通节点可以移动到阶段节点下。现有的同链校验、环路校验、子树路径和层级批量更新继续生效。

### 5.4 删除节点

`deleteNode()` 在统计子节点和企业挂接关系之前检查 `stageType`。

如果 `stageType != null`，返回 HTTP 400：

```text
上下游阶段节点不允许删除
```

删除整条产业链时，仍允许现有产业链删除流程统一逻辑删除其全部普通节点和阶段节点。

### 5.5 查询接口

以下响应增加只读字段 `stageType`：

- `NodeVO`
- `TreeNodeVO`

现有接口路径、请求方法和分页结构不变。新增字段对旧前端属于向后兼容扩展。

### 5.6 非法数据库值

后端不得将未知 `stage_type` 静默解释为普通节点。枚举映射或显式转换发现非法值时，应记录节点 ID、产业链 ID和非法值，并拒绝执行相关变更。

## 6. 数据迁移

新增迁移：

```text
back/src/main/resources/db/migration/V11__chain_stage_hierarchy.sql
```

迁移执行以下操作：

1. 新增 `stage_type` 字段。
2. 新增阶段唯一索引。
3. 为已知演示产业链创建三个阶段节点。
4. 回填阶段节点的规范路径。
5. 按明确映射更新细分节点的 `parent_id`、`node_level=2` 和 `node_path`。
6. 保留原细分节点 ID，企业挂接关系不变。

### 6.1 氢能源与新能源汽车链

| 阶段 | 细分节点 |
| --- | --- |
| 上游 | 氢气制取、氢气储运 |
| 中游 | 燃料电池、动力电池、电驱系统 |
| 下游 | 新能源整车、充换电设施 |

### 6.2 半导体链

| 阶段 | 细分节点 |
| --- | --- |
| 上游 | 芯片设计、半导体设备、半导体材料、EDA/IP |
| 中游 | 晶圆制造、封装测试、功率半导体 |
| 下游 | 暂无细分节点 |

### 6.3 光通信链

| 阶段 | 细分节点 |
| --- | --- |
| 上游 | 光芯片、光器件 |
| 中游 | 光模块、光纤光缆 |
| 下游 | 光通信设备、光通信测试仪器 |

### 6.4 人工智能链

| 阶段 | 细分节点 |
| --- | --- |
| 上游 | AI芯片/算力、AI基础软件 |
| 中游 | 大模型 |
| 下游 | AI应用、智能机器人、自动驾驶 |

迁移只处理稳定 ID、产业链 ID和节点名称均能确认的演示数据。未知产业链和无法确认归属的节点保持原状。

同步修改：

- `back/scripts/seed_data.sql`
- `back/scripts/seed_complete.sql`
- `back/src/test/resources/schema.sql`
- `back/src/test/resources/data.sql`
- `back/src/test/resources/init-data.sql`
- `back/src/test/resources/http/chain.http`

手工种子脚本应先确保阶段节点存在并回填阶段路径，再创建细分节点并回填细分节点路径。脚本重复执行时不得创建重复阶段节点。

## 7. 前端设计

### 7.1 类型与 API

`front/src/types/domain.ts` 增加 `ChainStageType`，并为 `ChainNode` 增加：

```typescript
stageType?: ChainStageType | null;
```

`front/src/api/chain.ts` 的节点响应适配器映射 `stageType`。

### 7.2 Mock 行为

`front/src/api/mock.ts` 与后端保持相同约束：

- 新建产业链自动创建三个阶段节点。
- 阶段节点允许改名。
- 阶段节点允许参与一级节点同级排序。
- 阶段节点移动和删除返回与后端一致的业务错误。
- 普通节点可以移动到阶段节点下。

### 7.3 产业链树

阶段节点显示独立类型标签：

- `UPSTREAM`：上游阶段
- `MIDSTREAM`：中游阶段
- `DOWNSTREAM`：下游阶段

标签根据 `stageType` 渲染，不根据可修改的节点名称推断。

阶段节点保留以下操作：

- 编辑
- 新增子节点
- 关联企业

删除按钮保持可见但禁用，悬停提示：

```text
上下游阶段节点不允许删除
```

### 7.4 节点调整

选中阶段节点时：

- 显示阶段节点保护提示。
- 禁用目标父节点选择。
- 禁用“提交移动”。
- 保留上移、下移和保存排序。

选中普通节点时，阶段节点仍是合法目标父节点。

前端只负责提前阻止明显非法操作，后端始终作为最终保护边界。

## 8. 空值与边界条件

Service 层必须进行防御性检查，不只依赖 Controller 和 Jakarta Validation。

| 场景 | 处理 |
| --- | --- |
| `createChain(null)` | HTTP 400，请求参数不能为空 |
| `createNode(null)` | HTTP 400，请求参数不能为空 |
| `updateNode(id, null)` | HTTP 400，请求参数不能为空 |
| `moveNode(id, null)` | HTTP 400，请求参数不能为空 |
| `sortNodes(null)` | HTTP 400，请求参数不能为空 |
| 名称为 `null`、空字符串或纯空白 | HTTP 400，名称不能为空 |
| `chainId` 或 `nodeId` 不存在 | 保持现有 404/400 契约，不写入数据 |
| 产业链插入后 ID 为空 | 抛出异常并回滚 |
| 任一阶段节点插入失败 | 抛出异常并回滚 |
| 阶段节点 `newParentId=null` | 仍拒绝移动 |
| `stageType=null` | 作为普通节点处理 |
| 数据库出现未知阶段值 | 记录错误并拒绝相关变更 |
| 重复阶段类型 | 唯一索引拒绝，事务回滚 |

固定阶段定义必须在代码中保证恰好包含三个非空、互不重复的枚举值。

## 9. 测试设计

### 9.1 后端

`ChainServiceTest` 和相关 Controller/Mapper 测试覆盖：

1. 新建产业链生成三个阶段节点。
2. 三个阶段节点的类型、名称、排序、层级和路径正确。
3. 阶段节点创建失败时事务整体回滚。
4. 阶段节点允许改名和排序。
5. 阶段节点移动失败。
6. 阶段节点单独删除失败。
7. 普通节点可以移动到阶段节点下。
8. 树接口和节点接口返回 `stageType`。
9. 普通历史节点 `stageType=null` 时行为不变。
10. 空请求、空白名称、资源不存在、重复阶段和非法阶段值均得到稳定错误。
11. 删除整条产业链仍能删除阶段节点。

数据库迁移验证：

1. 每条已迁移产业链恰有三个阶段节点。
2. 阶段节点路径和层级符合不变量。
3. 已知细分节点归属、层级和路径正确。
4. 企业挂接关系的节点 ID 不变。
5. 未知产业链和未知节点未被自动分类。

### 9.2 前端

`chain.spec.ts`、`ChainManagement.spec.ts`、`NodeAdjustment.spec.ts` 和 Mock 测试覆盖：

1. `stageType` 正确映射。
2. Mock 新建产业链生成三个阶段节点。
3. 阶段标签不受节点改名影响。
4. 阶段节点删除按钮禁用。
5. 阶段节点移动表单禁用。
6. 阶段节点仍可同级排序。
7. 普通节点仍可选择阶段节点作为目标父节点。
8. Mock 对非法移动和删除返回与后端一致的错误。
9. 缺失或为空的 `stageType` 按普通节点处理。

## 10. 验收标准

- 新建产业链后首次查询节点树即可看到三个阶段节点。
- 阶段节点可以改名，但类型标签保持不变。
- 阶段节点无法通过前端或直接 API 请求移动、单独删除。
- 普通节点可以添加或移动到阶段节点下。
- 项目内已知演示数据形成正确的上游、中游、下游树。
- 未知数据不会因名称或关键词被自动错误分类。
- 后端 `mvn test` 与 `mvn package` 通过。
- 前端 `npm run test` 与 `npm run build` 通过。

