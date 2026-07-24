<template>
  <div class="admin-page">
    <div class="page-header">
      <h1 class="page-title"><WpIcon name="admin" :size="22" class="title-icon" /> 管理后台</h1>
      <p class="page-desc">Agent 管理、运行观测、系统配置</p>
    </div>
    <div class="admin-grid">
      <router-link to="/usage" class="admin-card">
        <div class="card-icon"><WpIcon name="usage" :size="20" /></div>
        <div class="card-title">用量统计</div>
        <div class="card-desc">Token 消耗、调用次数、延迟分析</div>
      </router-link>
      <router-link to="/compare" class="admin-card">
        <div class="card-icon"><WpIcon name="compare" :size="20" /></div>
        <div class="card-title">Agent 对比</div>
        <div class="card-desc">多 Agent 回答质量对比</div>
      </router-link>
      <router-link to="/artifacts" class="admin-card">
        <div class="card-icon"><WpIcon name="artifact" :size="20" /></div>
        <div class="card-title">交付物管理</div>
        <div class="card-desc">查看所有 Agent 生产的交付物</div>
      </router-link>
      <div class="admin-card disabled">
        <div class="card-icon"><WpIcon name="search" :size="20" /></div>
        <div class="card-title">Trace 调试</div>
        <div class="card-desc">执行轨迹查看与分析（开发中）</div>
      </div>
      <div class="admin-card disabled">
        <div class="card-icon"><WpIcon name="admin" :size="20" /></div>
        <div class="card-title">系统设置</div>
        <div class="card-desc">模型配置、工具管理（开发中）</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import WpIcon from '../components/WpIcon.vue'
</script>

<style scoped>
.admin-page {
  padding: 32px 28px;
  height: 100%;
  overflow-y: auto;
}
.page-header { margin-bottom: 28px; }
.page-title {
  font-size: 22px;
  font-weight: 650;
  display: flex;
  align-items: center;
  gap: 10px;
  letter-spacing: -0.3px;
}
.title-icon { color: var(--gold-text); }
.page-desc { margin-top: 8px; color: var(--t3); font-size: 13px; }
.admin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}
.admin-card {
  display: block;
  padding: 20px;
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  text-decoration: none;
  color: inherit;
  transition: border-color 0.25s var(--ease), transform 0.25s var(--ease);
}
.admin-card:hover:not(.disabled) {
  border-color: rgba(245,158,11,0.28);
  transform: translateY(-2px);
}
.admin-card.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.card-icon {
  width: 40px; height: 40px;
  border-radius: 12px;
  display: grid; place-items: center;
  background: var(--gold-soft);
  border: 1px solid rgba(245,158,11,0.12);
  color: var(--gold-text);
  margin-bottom: 14px;
}
.card-title { font-size: 15px; font-weight: 600; margin-bottom: 6px; }
.card-desc { font-size: 12px; color: var(--t3); line-height: 1.55; }
</style>
