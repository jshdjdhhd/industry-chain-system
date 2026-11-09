import { get, post } from "./http";
import type { LoginRequest, LoginVO, CurrentUserVO } from "@/types/domain";

export function login(data: LoginRequest): Promise<LoginVO> {
  return post<LoginVO>("/auth/login", data);
}

export function logout(): Promise<void> {
  return post<void>("/auth/logout");
}

export function fetchCurrentUser(): Promise<CurrentUserVO> {
  return get<CurrentUserVO>("/auth/me");
}
