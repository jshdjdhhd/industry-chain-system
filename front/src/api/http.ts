import axios, { AxiosError, type AxiosRequestConfig } from "axios";
import { ElMessage } from "element-plus";
import type { ApiResponse } from "@/types/domain";
import { useAuthStore } from "@/stores/auth";
import router from "@/router";

export class ApiBusinessError extends Error {
  code: number;
  data: unknown;

  constructor(response: ApiResponse<unknown>) {
    super(response.message || "请求失败");
    this.name = "ApiBusinessError";
    this.code = response.code;
    this.data = response.data;
  }
}

export function unwrapApiResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== 200) {
    throw new ApiBusinessError(response as ApiResponse<unknown>);
  }

  return response.data;
}

function resolveErrorMessage(error: unknown): string {
  if (error instanceof ApiBusinessError) {
    return error.message;
  }

  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiResponse<unknown>>;
    return (
      axiosError.response?.data?.message ||
      axiosError.message ||
      "后端服务未连接"
    );
  }

  return error instanceof Error ? error.message : "请求失败";
}

const httpClient = axios.create({
  baseURL: "/api",
  timeout: 60_000, // 60 秒，LLM 分析可能较慢
});

httpClient.interceptors.request.use((config) => {
  const authStore = useAuthStore();
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`;
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => unwrapApiResponse(response.data),
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const authStore = useAuthStore();
      authStore.token = "";
      authStore.user = null;
      localStorage.removeItem("token");
      router.push("/login");
      ElMessage.error("登录已过期，请重新登录");
      return Promise.reject(error);
    }
    const message = resolveErrorMessage(error);
    ElMessage.error(message);
    return Promise.reject(error);
  },
);

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  return httpClient.request<unknown, T>(config);
}

export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ ...config, method: "GET", url });
}

export function post<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ ...config, method: "POST", url, data });
}

export function put<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ ...config, method: "PUT", url, data });
}

export function remove<T>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<T> {
  return request<T>({ ...config, method: "DELETE", url });
}

/**
 * 401 统一处理：清除登录态、跳转登录页并提示。httpClient 响应拦截器与
 * downloadBlob 的 catch 分支共用，避免登录过期提示不一致。
 */
function handleUnauthorized(): void {
  const authStore = useAuthStore();
  authStore.token = "";
  authStore.user = null;
  localStorage.removeItem("token");
  router.push("/login");
  ElMessage.error("登录已过期，请重新登录");
}

/**
 * 下载二进制文件。使用独立的 axios 实例，不挂响应拦截器（默认拦截器会按
 * ApiResponse 结构解包，对 blob 响应会抛 ApiBusinessError）。请求头手动注入
 * Authorization，baseURL 仍为 /api。
 */
const blobClient = axios.create({
  baseURL: "/api",
  timeout: 30_000,
});

export async function downloadBlob(
  url: string,
  config?: AxiosRequestConfig,
): Promise<Blob> {
  const authStore = useAuthStore();
  const headers: Record<string, string> = {
    ...(config?.headers as Record<string, string> | undefined),
  };
  if (authStore.token) {
    headers.Authorization = `Bearer ${authStore.token}`;
  }
  try {
    const response = await blobClient.get<Blob>(url, {
      ...config,
      responseType: "blob",
      headers,
    });
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      handleUnauthorized();
      return Promise.reject(error);
    }
    return Promise.reject(error);
  }
}
