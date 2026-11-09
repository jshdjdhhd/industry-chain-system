# 产业链管理系统前端

Vue 3 + TypeScript + Vite + Vue Router + Pinia + Element Plus。

## 启动

```bash
npm install
npm run dev
```

默认 dev server 端口为 `5173`。默认走后端联调模式，`/api` 会按 Vite 代理转发到 `http://localhost:8080`。

如需在后端未启动时临时查看页面，可显式开启 Mock：

```bash
VITE_API_MOCK=true npm run dev
```

## 常用命令

```bash
npm run format:check
npm test
npm run build
```

## 目录约定

- `src/api`：统一封装后端接口和本地 Mock 数据，组件不直接拼接后端地址。
- `src/router`：后台路由入口。
- `src/stores`：保存跨页面共享状态。
- `src/layouts`：后台整体布局、顶栏、侧栏和面包屑。
- `src/views`：企业、标签、产业链、智能挂链、数据看板页面。
- `src/components`：表格、筛选栏、状态标签、节点树等通用组件。

## 联调约定

接口响应按 `{ code, message, data }` 统一格式处理。后端返回非 `200` code 时，前端展示 `message`，最终业务规则校验由后端负责。
