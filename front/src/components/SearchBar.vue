<template>
  <section class="search-bar" aria-label="查询条件">
    <el-form :model="modelValue" class="search-bar__form" label-position="top">
      <slot />
      <div class="search-bar__actions">
        <slot name="actions" />
      </div>
    </el-form>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: Record<string, unknown>;
}>();
</script>

<style scoped>
.search-bar {
  backdrop-filter: blur(18px);
  background:
    linear-gradient(135deg, rgb(255 255 255 / 92%), rgb(244 250 255 / 80%)),
    radial-gradient(circle at 100% 0, rgb(8 191 211 / 10%), transparent 32%);
  border: 1px solid rgb(216 229 245 / 94%);
  border-radius: 8px;
  box-shadow: 0 16px 42px rgb(16 64 120 / 8%);
  min-width: 0;
  overflow: hidden;
  padding: 18px;
  position: relative;
}

.search-bar::before {
  background: linear-gradient(90deg, #1f6fff, #08bfd3);
  content: "";
  height: 2px;
  left: 18px;
  opacity: 0.7;
  position: absolute;
  right: 18px;
  top: 0;
}

.search-bar__form {
  align-items: end;
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  min-width: 0;
}

.search-bar__form :deep(.el-form-item__label) {
  color: #405674;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0;
}

.search-bar__form :deep(.el-form-item) {
  margin-bottom: 0;
  min-width: 0;
}

.search-bar__form :deep(.el-input),
.search-bar__form :deep(.el-select) {
  min-width: 0;
  width: 100%;
}

.search-bar__form :deep(.el-form-item__content),
.search-bar__form :deep(.el-input__wrapper),
.search-bar__form :deep(.el-select__wrapper) {
  min-width: 0;
}

.search-bar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  min-width: 0;
}

.search-bar__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (max-width: 720px) {
  .search-bar {
    padding: 14px;
  }

  .search-bar__form {
    grid-template-columns: 1fr;
  }

  .search-bar__actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    justify-content: flex-start;
  }

  .search-bar__actions :deep(.el-button) {
    min-width: 0;
    width: 100%;
  }
}
</style>
