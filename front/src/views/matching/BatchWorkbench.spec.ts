import { describe, expect, it } from "vitest";

describe("BatchWorkbench option loading", () => {
  it("loads lightweight options only after a selected scope is requested", async () => {
    const fs = await import("node:fs");
    const path = await import("node:path");
    const vuePath = path.resolve(__dirname, "./BatchWorkbench.vue");
    const source = fs.readFileSync(vuePath, "utf-8");

    expect(source).toContain("useOptionsStore");
    expect(source).toContain("batchForm.enterpriseScope");
    expect(source).toContain("batchForm.chainScope");
    expect(source).not.toContain("fetchEnterprises");
    expect(source).not.toContain("fetchChains");
    expect(source).not.toContain(
      "Promise.all([loadEnterprises(), loadChains()])",
    );
  });
});
