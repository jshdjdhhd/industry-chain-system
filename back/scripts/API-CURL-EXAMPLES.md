# 智能挂链模块 - API 调用示例 (curl)

> Base URL: `http://localhost:8080/api`
> 认证方式: JWT Bearer Token（登录后获取）

---

## 目录

1. [鉴权接口](#1-鉴权接口)
2. [智能挂链推荐接口](#2-智能挂链推荐接口)
3. [产业链节点接口](#3-产业链节点接口)
4. [健康检查](#4-健康检查)

---

## 1. 鉴权接口

### 1.1 用户登录

```bash
curl -s -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

响应示例：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "userId": 1,
    "username": "admin",
    "realName": "系统管理员",
    "permissions": ["enterprise:enterprise:query", "..."]
  }
}
```

### 1.2 获取当前用户信息

```bash
curl -s -X GET "http://localhost:8080/api/auth/me" \
  -H "Authorization: Bearer <TOKEN>"
```

### 1.3 退出登录

```bash
curl -s -X POST "http://localhost:8080/api/auth/logout" \
  -H "Authorization: Bearer <TOKEN>"
```

---

## 2. 智能挂链推荐接口

### 2.1 创建推荐任务

```bash
curl -s -X POST "http://localhost:8080/api/matching/tasks" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"enterpriseId":3,"chainId":1}'
```

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| enterpriseId | Long | 是 | 企业ID |
| chainId | Long | 是 | 产业链ID |

响应示例：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 5,
    "enterpriseId": 3,
    "chainId": 1,
    "status": "COMPLETED",
    "results": [
      {
        "resultId": 9,
        "nodeId": 201,
        "nodePath": "氢能源产业链 > 燃料电池",
        "confidence": 0.9600,
        "reason": "企业核心业务为燃料电池电堆制造，与燃料电池节点高度匹配",
        "evidence": "{\"keyword_match\":[\"燃料电池电堆\"],\"industry_match\":true}",
        "decision": "PENDING",
        "rejectReason": null
      }
    ]
  }
}
```

### 2.2 查询任务详情

```bash
curl -s -X GET "http://localhost:8080/api/matching/tasks/{taskId}" \
  -H "Authorization: Bearer <TOKEN>"
```

示例：
```bash
curl -s -X GET "http://localhost:8080/api/matching/tasks/1" \
  -H "Authorization: Bearer <TOKEN>"
```

### 2.3 采纳推荐结果

```bash
curl -s -X POST "http://localhost:8080/api/matching/results/{resultId}/accept" \
  -H "Authorization: Bearer <TOKEN>"
```

示例：
```bash
curl -s -X POST "http://localhost:8080/api/matching/results/9/accept" \
  -H "Authorization: Bearer <TOKEN>"
```

响应示例：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "resultId": 9,
    "decision": "ACCEPTED"
  }
}
```

### 2.4 拒绝推荐结果

```bash
curl -s -X POST "http://localhost:8080/api/matching/results/{resultId}/reject" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"rejectReason":"企业主营产品更偏动力电池，暂不采纳该节点"}'
```

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| rejectReason | String | 是 | 拒绝原因 |

### 2.5 人工确认挂链

```bash
curl -s -X POST "http://localhost:8080/api/matching/manual-confirm" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"taskId":3,"enterpriseId":7,"nodeId":301,"reason":"人工核验后确认比亚迪更适合挂接到新能源整车节点"}'
```

请求参数：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| taskId | Long | 是 | 任务ID |
| enterpriseId | Long | 是 | 企业ID |
| nodeId | Long | 是 | 目标节点ID |
| reason | String | 否 | 确认原因 |

响应示例：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "enterpriseId": 7,
    "nodeId": 301,
    "source": "MANUAL",
    "linked": true
  }
}
```

---

## 3. 产业链节点接口

### 3.1 挂链企业到节点

```bash
curl -s -X POST "http://localhost:8080/api/nodes/{nodeId}/enterprises" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"enterpriseId":5}'
```

示例：将企业5挂链到节点201（燃料电池）
```bash
curl -s -X POST "http://localhost:8080/api/nodes/201/enterprises" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"enterpriseId":5}'
```

### 3.2 从节点解绑企业

```bash
curl -s -X DELETE "http://localhost:8080/api/nodes/{nodeId}/enterprises/{enterpriseId}" \
  -H "Authorization: Bearer <TOKEN>"
```

示例：从节点201解绑企业5
```bash
curl -s -X DELETE "http://localhost:8080/api/nodes/201/enterprises/5" \
  -H "Authorization: Bearer <TOKEN>"
```

### 3.3 查询节点下企业列表

```bash
curl -s -X GET "http://localhost:8080/api/nodes/{nodeId}/enterprises" \
  -H "Authorization: Bearer <TOKEN>"
```

示例：查询燃料电池节点(201)下所有企业
```bash
curl -s -X GET "http://localhost:8080/api/nodes/201/enterprises" \
  -H "Authorization: Bearer <TOKEN>"
```

响应示例：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "enterpriseId": 3,
      "nodeId": 201,
      "source": "AI",
      "confidence": 0.9600,
      "reason": "AI推荐：亿华通匹配燃料电池节点",
      "confirmedBy": 1,
      "createTime": "2026-06-25T10:00:00",
      "updateTime": "2026-06-25T10:00:00"
    }
  ]
}
```

---

## 4. 健康检查

```bash
curl -s -X GET "http://localhost:8080/api/health"
```

---

## 快速联调流程

```bash
# 1. 登录
TOKEN=$(curl -s -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')

# 2. 创建任务
TASK=$(curl -s -X POST "http://localhost:8080/api/matching/tasks" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enterpriseId":3,"chainId":1}')

TASK_ID=$(echo $TASK | jq -r '.data.taskId')
RESULT_ID=$(echo $TASK | jq -r '.data.results[0].resultId')

# 3. 采纳推荐
curl -s -X POST "http://localhost:8080/api/matching/results/$RESULT_ID/accept" \
  -H "Authorization: Bearer $TOKEN"

# 4. 验证挂链
curl -s -X GET "http://localhost:8080/api/nodes/201/enterprises" \
  -H "Authorization: Bearer $TOKEN" | jq '.data'
```

---

## 响应格式说明

所有接口统一返回：
```json
{
  "code": 200,       // 业务状态码，200=成功
  "message": "success",
  "data": {}         // 业务数据（可能为 null）
}
```

错误响应示例：
```json
{
  "code": 400,
  "message": "企业ID不能为空",
  "data": null
}
```

---

## 演示数据节点 ID 参考

| 节点ID | 节点名称 | 层级 | 产业链 |
|--------|---------|------|--------|
| 101 | 氢气制取 | 上游 | 氢能源与新能源汽车零部件 |
| 102 | 氢气储运 | 上游 | 氢能源与新能源汽车零部件 |
| 201 | 燃料电池 | 中游 | 氢能源与新能源汽车零部件 |
| 202 | 动力电池 | 中游 | 氢能源与新能源汽车零部件 |
| 203 | 电驱系统 | 中游 | 氢能源与新能源汽车零部件 |
| 301 | 新能源整车 | 下游 | 氢能源与新能源汽车零部件 |
| 302 | 充换电设施 | 下游 | 氢能源与新能源汽车零部件 |

## 演示数据企业 ID 参考

| 企业ID | 企业名称 | 行业 | 关键词 |
|--------|---------|------|--------|
| 1 | 隆基氢能科技有限公司 | 氢气制取 | 电解水制氢,绿氢,碱性电解槽 |
| 2 | 中石化氢能有限公司 | 氢气储运 | 加氢站,高压储氢,液氢 |
| 3 | 北京亿华通科技股份有限公司 | 燃料电池 | 燃料电池电堆,氢燃料电池 |
| 4 | 宁德时代新能源科技股份有限公司 | 动力电池 | 动力电池,电芯,模组 |
| 5 | 精进电动科技股份有限公司 | 电驱系统 | 驱动电机,电控 |
| 6 | 大洋电机股份有限公司 | 电驱系统 | 驱动电机,新能源动力总成 |
| 7 | 比亚迪股份有限公司 | 整车制造 | 新能源汽车,纯电动 |
| 8 | 北京汽车股份有限公司 | 整车制造 | 北汽新能源,电动汽车 |
| 9 | 特锐德电气股份有限公司 | 充换电设施 | 充电桩,充电站 |
| 10 | 蔚来汽车科技有限公司 | 整车制造 | 换电站,新能源汽车 |
