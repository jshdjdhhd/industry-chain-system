import { describe, expect, it } from "vitest";
import { ApiBusinessError, unwrapApiResponse } from "./http";

describe("API response helpers", () => {
  it("returns the data payload from a successful unified response", () => {
    const data = unwrapApiResponse({
      code: 200,
      message: "success",
      data: { list: [], total: 0, pageNum: 1, pageSize: 10 },
    });

    expect(data).toEqual({ list: [], total: 0, pageNum: 1, pageSize: 10 });
  });

  it("throws a typed business error with backend message when code is non-zero", () => {
    expect(() =>
      unwrapApiResponse({
        code: 40001,
        message: "产业链节点存在子节点，不能删除",
        data: null,
      }),
    ).toThrow(ApiBusinessError);
  });
});
