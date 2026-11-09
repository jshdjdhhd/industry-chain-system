import { defineStore } from "pinia";
import { ref, computed } from "vue";
import * as authApi from "@/api/auth";
import type { CurrentUserVO } from "@/types/domain";

export const useAuthStore = defineStore("auth", () => {
  const token = ref<string>(localStorage.getItem("token") || "");
  const user = ref<CurrentUserVO | null>(null);
  const loading = ref(false);

  const isLoggedIn = computed(() => !!token.value);
  const userName = computed(() => user.value?.realName || user.value?.username || "");
  const permissions = computed(() => user.value?.permissions || []);

  async function login(username: string, password: string) {
    loading.value = true;
    try {
      const data = await authApi.login({ username, password });
      token.value = data.token;
      localStorage.setItem("token", data.token);
      user.value = {
        userId: data.userId,
        username: data.username,
        realName: data.realName,
        roles: [],
        permissions: data.permissions,
      };
      return data;
    } finally {
      loading.value = false;
    }
  }

  async function logout() {
    try {
      await authApi.logout();
    } finally {
      token.value = "";
      user.value = null;
      localStorage.removeItem("token");
    }
  }

  async function fetchUser() {
    if (!token.value) return;
    loading.value = true;
    try {
      user.value = await authApi.fetchCurrentUser();
    } catch {
      token.value = "";
      user.value = null;
      localStorage.removeItem("token");
    } finally {
      loading.value = false;
    }
  }

  return { token, user, loading, isLoggedIn, userName, permissions, login, logout, fetchUser };
});
