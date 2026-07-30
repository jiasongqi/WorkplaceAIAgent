<template>
  <div class="fav-layout">
    <div class="header">
      <div class="back-button" @click="goBack">← 返回</div>
      <h1 class="title"><WpIcon name="star" :size="20" class="title-icon" /> 我的收藏</h1>
      <div class="fav-count">{{ favorites.length }} 条</div>
    </div>

    <div class="content">
      <div v-if="loading" class="placeholder">加载中...</div>
      <div v-else-if="favorites.length === 0" class="placeholder">暂无收藏。在对话中点击消息旁的 ⭐ 即可收藏。</div>

      <div v-else class="fav-list">
        <div v-for="fav in favorites" :key="fav.favoriteId" class="fav-item" :class="{ orphaned: fav.orphaned }">
          <div class="fav-header">
            <span class="fav-source">来自: {{ fav.sessionTitleSnapshot }}</span>
            <span v-if="fav.orphaned" class="orphaned-badge">⚠️ 来源已删除</span>
            <span class="fav-role">{{ fav.role === 'user' ? '👤' : '🤖' }}</span>
          </div>
          <div class="fav-content">{{ fav.contentSnapshot }}</div>
          <div class="fav-footer">
            <span class="fav-time">{{ formatTime(fav.createdAt) }}</span>
            <button class="remove-btn" @click="doRemove(fav.favoriteId)">取消收藏</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listFavorites, removeFavorite } from '../api'
import WpIcon from '../components/WpIcon.vue'

const router = useRouter()
const favorites = ref([])
const loading = ref(true)

const goBack = () => router.push('/')

const loadFavorites = async () => {
  loading.value = true
  try {
    const res = await listFavorites()
    favorites.value = (res.data?.data || []).sort((a, b) => {
      return new Date(b.createdAt) - new Date(a.createdAt)
    })
  } catch (e) {
    console.error('加载收藏失败', e)
  } finally {
    loading.value = false
  }
}

const doRemove = async (favoriteId) => {
  try {
    await removeFavorite(favoriteId)
    favorites.value = favorites.value.filter(f => f.favoriteId !== favoriteId)
  } catch (e) {
    console.error('取消收藏失败', e)
  }
}

const formatTime = (value) => {
  if (!value) return '—'
  const d = new Date(value)
  if (isNaN(d.getTime())) return value
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(loadFavorites)
</script>

<style scoped>
.fav-layout {
  display: flex; flex-direction: column; height: 100vh;
  background: var(--bg-page, #0a0a0f); overflow: hidden;
}
.header {
  display: grid; grid-template-columns: 1fr auto 1fr; align-items: center;
  padding: 14px 24px; background: var(--glass-bg); color: white;
  border: 0.5px solid var(--border);
}
.back-button { cursor: pointer; font-size: 15px; opacity: 0.85; }
.back-button:hover { opacity: 1; }
.title { font-size: 18px; font-weight: bold; margin: 0; text-align: center; display: inline-flex; align-items: center; justify-content: center; gap: 8px; }
.title-icon { color: var(--gold-text); }
.fav-count { justify-self: end; font-size: 13px; opacity: 0.8; }

.content { flex: 1; overflow-y: auto; padding: 20px; max-width: 720px; margin: 0 auto; width: 100%; }
.placeholder { text-align: center; color: var(--text-muted); font-size: 14px; padding: 60px 16px; }

.fav-list { display: flex; flex-direction: column; gap: 12px; }
.fav-item {
  background: var(--surface); border-radius: var(--radius); padding: 16px;
  border: 0.5px solid var(--border); transition: all 0.2s;
}
.fav-item:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.12); }
.fav-item.orphaned { opacity: 0.7; border-left: 3px solid var(--warn); }

.fav-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.fav-source { font-size: 12px; color: var(--text-muted); }
.orphaned-badge { font-size: 11px; color: #92400e; background: #fef3c7; padding: 1px 6px; border-radius: 6px; }
.fav-role { font-size: 14px; margin-left: auto; }

.fav-content {
  font-size: 14px; color: var(--text, #e5e7eb); line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
  max-height: 120px; overflow: hidden; position: relative;
}

.fav-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.fav-time { font-size: 11px; color: var(--text-muted, #6b7280); }
.remove-btn {
  background: none; border: 1px solid var(--glass-border, rgba(255,255,255,0.1)); border-radius: 6px;
  padding: 4px 10px; font-size: 12px; color: var(--text-muted); cursor: pointer;
}
.remove-btn:hover { border-color: #dc2626; color: #dc2626; }
</style>
