<template>
  <!-- 路由视图容器 -->
  <router-view />
  <!-- 首页不展示 AI 助手悬浮按钮，登录后各业务页保留 -->
  <AiAssistant v-if="$route.path !== '/'" />
</template>

<script>
import AiAssistant from '@/components/AiAssistant.vue'

/**
 * 根组件
 * 作为整个应用的入口组件 */
export default {
  name: 'App',
  components: {
    AiAssistant
  }
}
</script>

<style>
/* 全局样式重置 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
  font-family: var(--zy-font-sans);
  background-color: var(--zy-bg-canvas);
  color: var(--zy-text-primary);
}

/* 滚动条样式统一在 modern-global.css 中定义 */

/* 移动端全局适配 */
@media screen and (max-width: 768px) {
  /* 弹窗适配 */
  .el-dialog {
    width: 90% !important;
    max-width: 90% !important;
  }
  
  .el-message-box {
    width: 90% !important;
    max-width: 90% !important;
  }

  /* 表格适配 */
  .el-table {
    width: 100% !important;
    overflow-x: auto !important;
  }
  .el-table .cell {
    padding: 0 5px !important;
    white-space: nowrap;
  }
  .el-table__body-wrapper {
    overflow-x: auto !important;
    -webkit-overflow-scrolling: touch;
  }
  .el-table__header-wrapper {
    overflow-x: auto !important;
  }
  /* 操作按钮在移动端换行显示 */
  .operation-btns {
    flex-wrap: wrap !important;
    gap: 4px !important;
  }
  .operation-btns .el-button {
    margin-left: 0 !important;
    font-size: 12px !important;
    padding: 4px 8px !important;
  }
  /* 固定列阴影在移动端去掉避免遮挡 */
  .el-table__fixed-right {
    box-shadow: none !important;
  }
  .el-table__fixed-right-patch {
    display: none !important;
  }
  .header-actions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
  }
  .header-actions .el-button {
    width: 100%;
    margin-left: 0 !important;
  }
  /* 搜索栏适配 */
  .search-bar {
    flex-direction: column !important;
  }
  .search-bar .el-input,
  .search-bar .el-select {
    width: 100% !important;
    margin-right: 0 !important;
  }
  .search-bar .el-button {
    width: 100% !important;
  }
  
  /* 分页适配 */
  .el-pagination {
    flex-wrap: wrap;
    justify-content: center;
  }

  /* 表单适配 */
  .el-form-item__label {
    float: none;
    display: block;
    text-align: left;
  }
  
  .el-form-item__content {
    margin-left: 0 !important;
  }
}
</style>
