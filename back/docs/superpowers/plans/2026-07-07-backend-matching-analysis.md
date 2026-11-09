# 后端智能挂链与 AI 分析实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 建立后端智能挂链任务、结果处理、AI 对话和评测能力，供前端智能挂链工作台稳定调用。

**架构：** Controller 只暴露统一响应接口，Service 管理任务生命周期和事务，Provider 封装匹配策略，Worker 执行异步任务，LLM 与 AI 对话能力通过 `git.ggwzz.llm` 与 `AiChatService` 接入。

**技术栈：** Java 21、Spring Boot 3.5.15、Spring Security、MyBatis-Plus、JUnit 5、Mockito、MockMvc。

---

## 文件结构

- 修改：`back/src/main/java/git/ggwzz/matching/controller/MatchingController.java`，暴露任务、结果、AI 对话和评测接口。
- 修改：`back/src/main/java/git/ggwzz/matching/service/MatchingService.java`，定义任务生命周期能力。
- 修改：`back/src/main/java/git/ggwzz/matching/service/impl/MatchingServiceImpl.java`，实现创建、查询、取消、撤回、重试、采纳、拒绝、人工确认。
- 修改：`back/src/main/java/git/ggwzz/matching/service/impl/MatchingTaskWorker.java`，执行异步任务。
- 修改：`back/src/main/java/git/ggwzz/matching/service/AiChatService.java` 与实现类，支持 AI 对话。
- 修改：`back/src/main/java/git/ggwzz/matching/service/impl/MatchingEvaluationService.java`，支持评测。
- 测试：`back/src/test/java/git/ggwzz/matching/controller/MatchingControllerTest.java`。
- 测试：`back/src/test/java/git/ggwzz/matching/service/MatchingServiceTest.java`。
- 测试：`back/src/test/java/git/ggwzz/matching/service/AiChatServiceImplTest.java`。
- 测试：`back/src/test/java/git/ggwzz/matching/service/MatchingEvaluationServiceTest.java`。

## 任务 1：Controller 接口合同

- [ ] **步骤 1：编写 MockMvc 测试**

在 `MatchingControllerTest` 中覆盖创建任务、异步任务、批量任务、查询任务、取消、撤回、重试、采纳、拒绝、人工确认、AI 对话和评测接口。

- [ ] **步骤 2：实现 Controller**

在 `MatchingController.java` 中只接收参数、校验权限、调用 Service 并返回 `Result.success(...)`。

- [ ] **步骤 3：运行 Controller 测试**

```bash
cd back
mvn -Dtest=MatchingControllerTest test
```

预期：接口路径、权限和统一响应测试通过。

## 任务 2：任务生命周期 Service

- [ ] **步骤 1：编写 Service 测试**

在 `MatchingServiceTest` 中覆盖创建任务保存结果、异步任务进入处理中、批量任务提交、取消、撤回、重试、采纳、拒绝和人工确认。

- [ ] **步骤 2：实现生命周期方法**

在 `MatchingServiceImpl` 中实现任务写入、状态校验、结果处理、事务保护和 VO 组装。

- [ ] **步骤 3：运行 Service 测试**

```bash
cd back
mvn -Dtest=MatchingServiceTest test
```

预期：任务状态和结果处理测试通过。

## 任务 3：异步执行与 Provider

- [ ] **步骤 1：编写 Worker 和 Provider 测试**

覆盖任务成功、任务失败、无候选结果、Provider 抛异常和批量任务执行。

- [ ] **步骤 2：实现异步执行**

在 `MatchingTaskWorker` 中读取任务上下文，调用匹配 Provider，保存结果并更新状态。

- [ ] **步骤 3：运行匹配服务测试**

```bash
cd back
mvn -Dtest=MatchingTaskWorkerTest,MatchingServiceTest test
```

预期：异步任务状态流转测试通过。

## 任务 4：AI 对话与评测

- [ ] **步骤 1：编写 AI 对话测试**

在 `AiChatServiceImplTest` 中覆盖上下文构建、模型调用、空上下文提示和模型失败。

- [ ] **步骤 2：编写评测测试**

在 `MatchingEvaluationServiceTest` 中覆盖评测样本、评测指标和报告生成。

- [ ] **步骤 3：实现服务**

在 `AiChatServiceImpl` 与 `MatchingEvaluationService` 中实现对应业务逻辑，外部模型异常转为可读业务错误。

- [ ] **步骤 4：运行 AI 和评测测试**

```bash
cd back
mvn -Dtest=AiChatServiceImplTest,MatchingEvaluationServiceTest test
```

预期：AI 对话和评测服务测试通过。

## 任务 5：最终验证

- [ ] **步骤 1：运行匹配相关测试**

```bash
cd back
mvn -Dtest=MatchingControllerTest,MatchingServiceTest,MatchingTaskWorkerTest,AiChatServiceImplTest,MatchingEvaluationServiceTest test
```

预期：匹配模块测试通过。

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

预期：生成 `target/industry-chain-1.0.0.jar`。
