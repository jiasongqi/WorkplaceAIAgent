<template>
  <div class="admin-page">
    <div class="page-header">
      <h1 class="page-title">管理后台</h1>
      <p class="page-desc">Agent 管理、运行观测、系统配置</p>
    </div>
    <div class="admin-grid">
      <router-link to="/usage" class="admin-card">
        <div class="card-title">用量统计</div>
        <div class="card-desc">Token 消耗、调用次数、延迟分析</div>
      </router-link>
      <router-link to="/compare" class="admin-card">
        <div class="card-title">Agent 对比</div>
        <div class="card-desc">多 Agent 回答质量对比</div>
      </router-link>
      <router-link to="/artifacts" class="admin-card">
        <div class="card-title">交付物管理</div>
        <div class="card-desc">查看所有 Agent 生产的交付物</div>
      </router-link>
      <div class="admin-card disabled">
        <div class="card-title">Trace 调试</div>
        <div class="card-desc">执行轨迹查看与分析（开发中）</div>
      </div>
      <div class="admin-card disabled">
        <div class="card-title">系统设置</div>
        <div class="card-desc">模型配置、工具管理（开发中）</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; max-width: 960px; margin: 0 auto; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 20px; font-weight: 600; color: var(--text); margin-bottom: 4px; }
.page-desc { font-size: 13px; color: var(--text-muted); }
.admin-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; }
.admin-card {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 20px; text-decoration: none; transition: all 0.15s; cursor: pointer;
}
.admin-card:hover { border-color: var(--primary); box-shadow: var(--shadow); }
.admin-card.disabled { opacity: 0.5; cursor: not-allowed; }
.admin-card.disabled:hover { border-color: var(--border); box-shadow: none; }
.card-title { font-size: 14px; font-weight: 600; color: var(--text); margin-bottom: 4px; }
.card-desc { font-size: 12px; color: var(--text-muted); }
</style>
