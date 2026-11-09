import { get, post, put, remove } from "./http";
import { isMockApiEnabled } from "./mode";
import { mockApi } from "./mock";
import type { Tag } from "@/types/domain";

export interface TagPayload {
  name: string;
  type: string;
  color?: string;
  description?: string;
}

export function fetchTags(params?: {
  type?: string;
  keyword?: string;
}): Promise<Tag[]> {
  if (isMockApiEnabled()) return mockApi.fetchTags(params);
  return get("/tags", { params });
}

export function createTag(data: TagPayload): Promise<Tag> {
  if (isMockApiEnabled()) return mockApi.createTag(data);
  return post("/tags", data);
}

export function updateTag(id: number, data: TagPayload): Promise<Tag> {
  if (isMockApiEnabled()) return mockApi.updateTag(id, data);
  return put(`/tags/${id}`, data);
}

export function deleteTag(id: number): Promise<void> {
  if (isMockApiEnabled()) return mockApi.deleteTag(id);
  return remove(`/tags/${id}`);
}
