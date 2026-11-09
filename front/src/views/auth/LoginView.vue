<script setup lang="ts">
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { User, Lock } from "@element-plus/icons-vue";
import { useAuthStore } from "@/stores/auth";

const router = useRouter();
const authStore = useAuthStore();

const form = reactive({ username: "", password: "" });
const loading = ref(false);

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning("请输入用户名和密码");
    return;
  }
  loading.value = true;
  try {
    await authStore.login(form.username, form.password);
    ElMessage.success("登录成功");
    router.push("/dashboard");
  } catch {
    // http interceptor already shows error
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-shell" aria-label="用户登录">
      <header class="login-heading">
        <h1>产业链管理系统</h1>
        <p>链接产业要素 · 智能驱动发展</p>
      </header>

      <div class="login-card">
        <h2 class="login-card__title">用户登录</h2>
        <el-form @submit.prevent="handleLogin">
          <el-form-item>
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-card__submit"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  align-items: center;
  background:
    linear-gradient(90deg, rgb(2 8 24 / 74%), rgb(4 21 51 / 54%)),
    url("/login-tech-bg.png") center / cover;
  display: flex;
  justify-content: center;
  min-height: 100vh;
  overflow: hidden;
  padding: 28px;
  position: relative;
}

.login-page::before {
  background:
    radial-gradient(circle at 24% 42%, rgb(0 209 255 / 24%), transparent 28%),
    radial-gradient(circle at 78% 32%, rgb(31 111 255 / 28%), transparent 30%),
    linear-gradient(180deg, transparent 0%, rgb(2 8 24 / 45%) 100%);
  content: "";
  inset: 0;
  opacity: 1;
  position: absolute;
}

.login-page::after {
  background:
    linear-gradient(
      115deg,
      transparent 0 35%,
      rgb(99 214 255 / 13%) 42%,
      transparent 49%
    ),
    linear-gradient(
      90deg,
      rgb(255 255 255 / 12%),
      transparent 16% 84%,
      rgb(255 255 255 / 10%)
    );
  content: "";
  inset: 0;
  mix-blend-mode: screen;
  opacity: 0.7;
  pointer-events: none;
  position: absolute;
}

.login-shell {
  backdrop-filter: blur(22px);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 76%), rgb(255 255 255 / 56%)),
    rgb(255 255 255 / 46%);
  border: 1px solid rgb(255 255 255 / 50%);
  border-radius: 10px;
  box-shadow:
    0 28px 80px rgb(0 25 70 / 28%),
    0 1px 0 rgb(255 255 255 / 72%) inset;
  max-width: 430px;
  overflow: hidden;
  padding: 30px 28px 32px;
  position: relative;
  width: min(100%, 430px);
  z-index: 1;
}

.login-shell::before {
  background: linear-gradient(90deg, #1f6fff, #08bfd3);
  border-radius: 999px;
  content: "";
  height: 3px;
  left: 28px;
  opacity: 0.95;
  position: absolute;
  right: 28px;
  top: 0;
}

.login-heading {
  margin-bottom: 28px;
}

.login-heading h1 {
  color: #0d2648;
  font-size: 36px;
  font-weight: 900;
  letter-spacing: 0;
  line-height: 1.08;
  margin: 0;
  text-shadow: 0 8px 22px rgb(10 37 74 / 12%);
}

.login-heading p {
  color: #6f829a;
  font-size: 14px;
  font-weight: 700;
  margin: 10px 0 0;
}

.login-card {
  display: grid;
  gap: 18px;
}

.login-card__title {
  color: #102033;
  font-size: 30px;
  font-weight: 800;
  margin: 0;
}

.login-card__subtitle {
  color: #63738b;
  font-size: 14px;
  margin: 8px 0 0;
}

.login-card :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-card :deep(.el-input__wrapper) {
  background: rgb(255 255 255 / 78%);
  border-radius: 6px;
  min-height: 52px;
}

.login-card__submit {
  height: 52px;
  margin-top: 6px;
  width: 100%;
}

@media (max-width: 860px) {
  .login-shell {
    padding: 28px 22px 30px;
  }

  .login-heading h1 {
    font-size: 30px;
  }
}
</style>
