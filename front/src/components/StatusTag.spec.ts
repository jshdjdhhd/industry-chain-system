import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import StatusTag from "./StatusTag.vue";

describe("StatusTag", () => {
  it("renders enterprise status labels from backend enum values", () => {
    const wrapper = mount(StatusTag, {
      props: {
        status: "UNREGISTERED",
      },
    });

    expect(wrapper.text()).toContain("未入库");
  });

  it("falls back to the raw status when the enum is not known yet", () => {
    const wrapper = mount(StatusTag, {
      props: {
        status: "ARCHIVED",
      },
    });

    expect(wrapper.text()).toContain("ARCHIVED");
  });
});
