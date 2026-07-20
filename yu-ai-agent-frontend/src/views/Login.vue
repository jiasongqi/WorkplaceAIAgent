<template>
  <div class="login-page">
    <div class="login-panel">
      <h1>WorkPilot</h1>
      <p class="sub">登录后使用职场智囊</p>

      <div class="tabs">
        <button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button type="button" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <label>用户名</label>
      <input v-model="username" autocomplete="username" placeholder="2-32 字符" />

      <label>密码</label>
      <input v-model="password" type="password" autocomplete="current-password" placeholder="至少 6 位" @keyup.enter="submit" />

      <p v-if="error" class="error">{{ error }}</p>

      <button type="button" class="primary" :disabled="loading" @click="submit">
        {{ loading ? '请稍候…' : (mode === 'login' ? '登录' : '注册并登录') }}
      </button>

      <button type="button" class="ghost" :disabled="loading" @click="guestLogin">
        一键游客登录（本地联调）
      </button>

      <p class="hint">
        新用户请先点「注册」。本地游客：用户名 <code>游客</code>，密码 <code>workpilot-local</code>；
        管理员：用户名 <code>admin</code>，密码 <code>admin-local</code>。
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { loginWithPassword, register } from '../api'

const router = useRouter()
const route = useRoute()
const mode = ref('login')
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

function pickError(e) {
  return e?.response?.data?.message
    || e?.message
    || '登录失败，请检查网络或后端是否已启动'
}

async function goAfterAuth() {
  // 默认回首页；若从受保护页跳来则尊重 redirect（但排除登录页自身）
  const raw = route.query.redirect
  const redirect = (typeof raw === 'string' && raw && !raw.startsWith('/login'))
    ? raw
    : '/'
  await router.replace(redirect)
}

async function submit() {
  error.value = ''
  const u = username.value.trim()
  const p = password.value
  if (!u || u.length < 2) {
    error.value = '请填写用户名（至少 2 个字符）'
    return
  }
  if (!p || p.length < 6) {
    error.value = '请填写密码（至少 6 位）'
    return
  }

  loading.value = true
  try {
    if (mode.value === 'register') {
      await register(u, p)
    } else {
      await loginWithPassword(u, p)
    }
    await goAfterAuth()
  } catch (e) {
    error.value = pickError(e)
  } finally {
    loading.value = false
  }
}

async function guestLogin() {
  error.value = ''
  loading.value = true
  try {
    await loginWithPassword('游客', 'workpilot-local')
    await goAfterAuth()
  } catch (e) {
    error.value = pickError(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: linear-gradient(160deg, #0f172a, #1e293b 45%, #0ea5e9);
  padding: 24px;
}
.login-panel {
  width: min(400px, 100%);
  background: #fff;
  border-radius: 16px;
  padding: 28px 24px;
  box-shadow: 0 20px 50px rgba(0,0,0,.25);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
h1 { margin: 0; font-size: 1.75rem; letter-spacing: .02em; }
.sub { margin: 0 0 8px; color: #64748b; font-size: .9rem; }
.tabs { display: flex; gap: 8px; margin-bottom: 4px; }
.tabs button {
  flex: 1; border: 1px solid #e2e8f0; background: #f8fafc; padding: 8px;
  border-radius: 8px; cursor: pointer;
}
.tabs button.active { background: #0f172a; color: #fff; border-color: #0f172a; }
label { font-size: .85rem; color: #475569; }
input {
  border: 1px solid #cbd5e1; border-radius: 8px; padding: 10px 12px; font-size: 1rem;
}
.primary {
  margin-top: 8px; background: #0284c7; color: #fff; border: 0; border-radius: 8px;
  padding: 12px; font-weight: 600; cursor: pointer;
}
.primary:disabled, .ghost:disabled { opacity: .6; cursor: not-allowed; }
.ghost {
  background: #f1f5f9; color: #0f172a; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 10px; cursor: pointer; font-weight: 500;
}
.hint { margin: 4px 0 0; color: #94a3b8; font-size: .8rem; line-height: 1.4; }
.hint code { background: #f1f5f9; padding: 1px 4px; border-radius: 4px; color: #475569; }
.error { color: #dc2626; margin: 0; font-size: .9rem; }
</style>
