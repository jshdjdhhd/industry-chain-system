import { describe, expect, it } from "vitest";
import { routes } from "./index";

describe("router configuration", () => {
  it("redirects the root route to the dashboard", () => {
    expect(routes.find((route) => route.path === "/")?.redirect).toBe(
      "/dashboard",
    );
  });

  it("exposes the MVP routes defined in the frontend spec", () => {
    const childPaths = routes.flatMap(
      (route) => route.children?.map((child) => child.path) ?? [],
    );

    expect(childPaths).toEqual(
      expect.arrayContaining([
        "/enterprises",
        "/enterprises/unregistered",
        "/enterprises/outbound",
        "/enterprises/import-records",
        "/enterprises/:id",
        "/enterprises/new",
        "/enterprises/:id/edit",
        "/tags",
        "/chains",
        "/chains/node-search",
        "/chains/node-adjustment",
        "/matching",
        "/dashboard",
      ]),
    );
  });

  it("keeps page titles in route meta for layout and breadcrumb rendering", () => {
    const titledRoutes = routes
      .flatMap((route) => route.children ?? [])
      .filter((route) => route.path);

    expect(
      titledRoutes.every((route) => typeof route.meta?.title === "string"),
    ).toBe(true);
  });

  it("locks the outbound enterprise page to OUTBOUND status", () => {
    const outboundRoute = routes
      .flatMap((route) => route.children ?? [])
      .find((route) => route.path === "/enterprises/outbound");

    expect(outboundRoute).toMatchObject({
      name: "enterprise-outbound",
      meta: {
        title: "出库企业",
        listTitle: "出库企业",
        defaultStatus: "OUTBOUND",
        lockedStatus: true,
      },
    });
  });

  it("lazy-loads the independent node adjustment page", async () => {
    const nodeAdjustmentRoute = routes
      .flatMap((route) => route.children ?? [])
      .find((route) => route.path === "/chains/node-adjustment");

    expect(nodeAdjustmentRoute).toMatchObject({
      name: "chain-node-adjustment",
      meta: { title: "节点调整" },
    });
    expect(typeof nodeAdjustmentRoute?.component).toBe("function");
    await expect(
      (
        nodeAdjustmentRoute?.component as () => Promise<{
          default: { __name?: string };
        }>
      )(),
    ).resolves.toMatchObject({
      default: { __name: "NodeAdjustment" },
    });
  });

  it("仅系统管理页面标记隐藏 AI 助手", () => {
    const childRoutes = routes.flatMap((route) => route.children ?? []);
    const systemManage = childRoutes.find(
      (route) => route.path === "/system/manage",
    );
    const operationLogs = childRoutes.find(
      (route) => route.path === "/system/operation-logs",
    );
    const dashboard = childRoutes.find((route) => route.path === "/dashboard");

    expect(systemManage?.meta?.hideAiAssistant).toBe(true);
    expect(operationLogs?.meta?.hideAiAssistant).toBe(true);
    expect(dashboard?.meta?.hideAiAssistant).toBeUndefined();
  });
});
