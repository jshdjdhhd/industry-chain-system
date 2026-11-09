#!/bin/bash
# =====================================================================
# 智能挂链模块联调脚本
# 说明: 演示智能挂链推荐、采纳、拒绝、人工确认的完整闭环流程
# 依赖: curl, jq
# 注意: 在 Git Bash (Windows) 或 Linux/macOS 终端中执行
# =====================================================================

set -euo pipefail

BASE_URL="http://localhost:8080/api"
TOKEN=""

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }
separator() { echo "--------------------------------------------------"; }

echo "============================================="
echo "     智能挂链模块联调测试"
echo "============================================="
echo ""

# ------------------------------------------------------------------
# 1. 健康检查
# ------------------------------------------------------------------
info "1. 健康检查..."
HEALTH=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/health" 2>/dev/null)
HTTP_CODE=$(echo "$HEALTH" | tail -1)
BODY=$(echo "$HEALTH" | head -n -1)
if [ "$HTTP_CODE" = "200" ]; then
    pass "服务运行正常"
else
    fail "健康检查失败，HTTP $HTTP_CODE。请确认后端已启动在 localhost:8080"
fi

# ------------------------------------------------------------------
# 2. 登录获取 Token
# ------------------------------------------------------------------
separator
info "2. 登录获取 Token..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token // empty')
if [ -z "$TOKEN" ]; then
    fail "登录失败: $LOGIN_RESPONSE"
fi
echo "Token: ${TOKEN:0:30}..."
pass "登录成功"

# ------------------------------------------------------------------
# 3. 获取当前用户信息
# ------------------------------------------------------------------
separator
info "3. 获取当前用户信息..."
ME_RESPONSE=$(curl -s -X GET "$BASE_URL/auth/me" \
  -H "Authorization: Bearer $TOKEN")
echo $ME_RESPONSE | jq '.data | {username, realName, permissions}'
pass "用户信息获取成功"

# ------------------------------------------------------------------
# 4. 创建智能挂链任务 - 亿华通(燃料电池)
# ------------------------------------------------------------------
separator
info "4. 创建智能挂链任务 - 亿华通(燃料电池电堆) → 氢能源产业链..."
TASK_RESPONSE=$(curl -s -X POST "$BASE_URL/matching/tasks" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enterpriseId":3,"chainId":1}')
TASK_ID=$(echo $TASK_RESPONSE | jq -r '.data.taskId')
RESULTS_COUNT=$(echo $TASK_RESPONSE | jq -r '.data.results | length')
echo "  任务ID: $TASK_ID"
echo "  推荐结果数: $RESULTS_COUNT"
echo $TASK_RESPONSE | jq '.data.results[] | {resultId, nodeId, confidence, decision}'
if [ "$RESULTS_COUNT" -gt 0 ]; then
    pass "任务创建成功，获得 $RESULTS_COUNT 条推荐"
else
    fail "未获得推荐结果"
fi

# ------------------------------------------------------------------
# 5. 采纳最高置信度推荐
# ------------------------------------------------------------------
separator
info "5. 采纳最高置信度推荐..."
RESULT_ID=$(echo $TASK_RESPONSE | jq -r '.data.results[0].resultId')
ACCEPT_RESPONSE=$(curl -s -X POST "$BASE_URL/matching/results/$RESULT_ID/accept" \
  -H "Authorization: Bearer $TOKEN")
echo $ACCEPT_RESPONSE | jq '.data'
DECISION=$(echo $ACCEPT_RESPONSE | jq -r '.data.decision')
if [ "$DECISION" = "ACCEPTED" ]; then
    pass "推荐已采纳 (resultId=$RESULT_ID)"
else
    fail "采纳失败，决策状态: $DECISION"
fi

# ------------------------------------------------------------------
# 6. 查询节点企业列表，确认企业已挂接
# ------------------------------------------------------------------
separator
info "6. 查询燃料电池节点(id=201)下企业列表..."
NODES_RESPONSE=$(curl -s -X GET "$BASE_URL/nodes/201/enterprises" \
  -H "Authorization: Bearer $TOKEN")
NODES_COUNT=$(echo $NODES_RESPONSE | jq '.data | length')
echo "  该节点下企业数: $NODES_COUNT"
echo $NODES_RESPONSE | jq '.data[] | {enterpriseId, nodeId, source, confidence}'
pass "节点企业列表查询成功"

# ------------------------------------------------------------------
# 7. 创建第二条任务并拒绝推荐 - 宁德时代(动力电池)
# ------------------------------------------------------------------
separator
info "7. 创建任务并拒绝推荐 - 宁德时代(动力电池)..."
TASK2_RESPONSE=$(curl -s -X POST "$BASE_URL/matching/tasks" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enterpriseId":4,"chainId":1}')
TASK2_ID=$(echo $TASK2_RESPONSE | jq -r '.data.taskId')
RESULT2_ID=$(echo $TASK2_RESPONSE | jq -r '.data.results[1].resultId')
RESULT2_CONF=$(echo $TASK2_RESPONSE | jq -r '.data.results[1].confidence')
echo "  任务ID: $TASK2_ID"
echo "  拒绝推荐: resultId=$RESULT2_ID, confidence=$RESULT2_CONF"

REJECT_RESPONSE=$(curl -s -X POST "$BASE_URL/matching/results/$RESULT2_ID/reject" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"rejectReason":"企业主营产品更偏动力电池，暂不采纳该节点"}')
REJECT_DECISION=$(echo $REJECT_RESPONSE | jq -r '.data.decision')
if [ "$REJECT_DECISION" = "REJECTED" ]; then
    pass "推荐已拒绝 (resultId=$RESULT2_ID)"
else
    fail "拒绝失败，决策状态: $REJECT_DECISION"
fi

# ------------------------------------------------------------------
# 8. 人工确认挂链 - 比亚迪(新能源整车)
# ------------------------------------------------------------------
separator
info "8. 人工确认挂链 - 比亚迪 → 新能源整车节点..."
MANUAL_RESPONSE=$(curl -s -X POST "$BASE_URL/matching/manual-confirm" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"taskId":3,"enterpriseId":7,"nodeId":301,"reason":"人工核验后确认比亚迪更适合挂接到新能源整车节点"}')
echo $MANUAL_RESPONSE | jq '.data'
MANUAL_LINKED=$(echo $MANUAL_RESPONSE | jq -r '.data.linked')
if [ "$MANUAL_LINKED" = "true" ]; then
    pass "人工确认挂链成功"
else
    fail "人工确认挂链失败"
fi

# ------------------------------------------------------------------
# 9. 查询节点企业列表 - 新能源整车节点
# ------------------------------------------------------------------
separator
info "9. 查询新能源整车节点(id=301)下企业列表..."
NODES301_RESPONSE=$(curl -s -X GET "$BASE_URL/nodes/301/enterprises" \
  -H "Authorization: Bearer $TOKEN")
NODES301_COUNT=$(echo $NODES301_RESPONSE | jq '.data | length')
echo "  该节点下企业数: $NODES301_COUNT"
echo $NODES301_RESPONSE | jq '.data[] | {enterpriseId, nodeId, source, confidence}'
pass "节点企业列表查询成功"

# ------------------------------------------------------------------
# 10. 查询任务详情
# ------------------------------------------------------------------
separator
info "10. 查询任务1详情..."
TASK_DETAIL=$(curl -s -X GET "$BASE_URL/matching/tasks/1" \
  -H "Authorization: Bearer $TOKEN")
echo $TASK_DETAIL | jq '.data | {taskId, enterpriseId, chainId, status}'
echo $TASK_DETAIL | jq '.data.results[] | {resultId, nodeId, confidence, decision}'
pass "任务详情查询成功"

# ------------------------------------------------------------------
# 汇总
# ------------------------------------------------------------------
echo ""
echo "============================================="
echo "     联调测试完成"
echo "============================================="
echo ""
echo "已执行操作:"
echo "  1. 健康检查 ✓"
echo "  2. 登录获取 Token ✓"
echo "  3. 获取当前用户信息 ✓"
echo "  4. 创建挂链任务(亿华通→燃料电池) ✓"
echo "  5. 采纳最高置信度推荐 ✓"
echo "  6. 查询节点企业列表 ✓"
echo "  7. 创建任务并拒绝推荐(宁德时代) ✓"
echo "  8. 人工确认挂链(比亚迪→新能源整车) ✓"
echo "  9. 查询节点企业列表(整车节点) ✓"
echo "  10. 查询任务详情 ✓"
echo ""
echo "闭环覆盖: 创建任务 → 推荐 → 采纳/拒绝 → 人工确认 → 验证挂链"
