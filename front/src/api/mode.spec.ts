import { describe, expect, it } from "vitest";
import { isMockApiEnabled } from "./mode";

describe("API mode", () => {
  it("uses backend proxy mode by default for integration", () => {
    expect(isMockApiEnabled(undefined)).toBe(false);
  });

  it("can be switched to backend proxy mode explicitly", () => {
    expect(isMockApiEnabled("false")).toBe(false);
    expect(isMockApiEnabled("0")).toBe(false);
  });

  it("can be switched to mock mode explicitly", () => {
    expect(isMockApiEnabled("true")).toBe(true);
    expect(isMockApiEnabled("mock")).toBe(true);
  });
});
