<template>
  <div class="login-page">
    <div class="login-stage">
      <!-- Brand -->
      <header class="brand-block">
        <div class="logo-orb" aria-hidden="true"></div>
        <h1 class="brand-name">WorkPilot</h1>
        <p class="brand-tag">不管你在烦恼什么，都可以先放一放。<br>登录后，这里只有选择，没有对错。</p>
      </header>

      <!-- Auth panel -->
      <form class="auth-panel" @submit.prevent="submit">
        <div class="mode-switch" role="tablist">
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'login'"
            :class="{ active: mode === 'login' }"
            @click="mode = 'login'"
          >登录</button>
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'register'"
            :class="{ active: mode === 'register' }"
            @click="mode = 'register'"
          >注册</button>
          <span class="mode-pill" :class="mode" aria-hidden="true"></span>
        </div>

        <div class="field">
          <label for="login-username">用户名</label>
          <input
            id="login-username"
            v-model="username"
            autocomplete="username"
            placeholder="2–32 个字符"
            :disabled="loading"
          />
        </div>

        <div class="field">
          <label for="login-password">密码</label>
          <div class="password-wrap">
            <input
              id="login-password"
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="至少 6 位"
              :disabled="loading"
              @keyup.enter="submit"
            />
            <button
              type="button"
              class="toggle-vis"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >{{ showPassword ? '隐' : '显' }}</button>
          </div>
        </div>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <button type="submit" class="btn-primary" :disabled="loading">
          <span v-if="loading" class="btn-spin" aria-hidden="true"></span>
          {{ loading ? '请稍候…' : (mode === 'login' ? '进入 WorkPilot' : '注册并进入') }}
        </button>

        <button type="button" class="btn-ghost" :disabled="loading" @click="guestLogin">
          一键游客体验
        </button>

        <p class="trust">对话仅你可见 · 可随时导出或删除</p>
      </form>

      <details class="dev-hint">
        <summary>本地联调账号</summary>
        <p>
          游客：用户名 <code>游客</code>，密码 <code>workpilot-local</code><br>
          管理员：用户名 <code>admin</code>，密码 <code>admin-local</code>
        </p>
      </details>
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
const showPassword = ref(false)

function pickError(e) {
  return e?.response?.data?.message
    || e?.message
    || '登录失败，请检查网络或后端是否已启动'
}

async function goAfterAuth() {
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
  height: 100%;
  display: grid;
  place-items: center;
  padding: 32px 20px;
  overflow-y: auto;
}

.login-stage {
  width: min(400px, 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 28px;
}

/* —— Brand —— */
.brand-block {
  text-align: center;
  animation: rise 0.7s var(--ease) both;
}

.logo-orb {
  width: 44px;
  height: 44px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, rgba(245, 158, 11, 0.9), rgba(217, 119, 6, 0.35));
  box-shadow: 0 0 18px var(--gold-glow), inset 0 1px 0 rgba(255, 255, 255, 0.25);
  animation: orb-pulse 5s ease-in-out infinite;
}

.brand-name {
  margin: 0;
  font-size: 36px;
  font-weight: 650;
  letter-spacing: -0.8px;
  line-height: 1.15;
  background: linear-gradient(180deg, var(--t1) 25%, rgba(242, 242, 248, 0.55));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.brand-tag {
  margin: 14px 0 0;
  font-size: 14px;
  color: var(--t3);
  line-height: 1.7;
  font-weight: 400;
}

/* —— Panel —— */
.auth-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px 22px 20px;
  background: var(--glass);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  backdrop-filter: blur(20px);
  box-shadow: var(--card-hover-shadow);
  animation: rise 0.7s 0.12s var(--ease) both;
}

.mode-switch {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  padding: 4px;
  background: var(--layer2);
  border-radius: var(--r-sm);
  margin-bottom: 6px;
}

.mode-switch button {
  position: relative;
  z-index: 1;
  padding: 9px 12px;
  font-size: 13px;
  font-weight: 550;
  color: var(--t3);
  border-radius: 8px;
  transition: color 0.25s var(--ease);
}

.mode-switch button.active {
  color: var(--t1);
}

.mode-pill {
  position: absolute;
  top: 4px;
  bottom: 4px;
  width: calc(50% - 4px);
  left: 4px;
  border-radius: 8px;
  background: var(--layer3);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.04);
  transition: transform 0.28s var(--spring);
  z-index: 0;
}

.mode-pill.register {
  transform: translateX(100%);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.field label {
  font-size: 12px;
  font-weight: 500;
  color: var(--t3);
  letter-spacing: 0.02em;
}

.field input {
  width: 100%;
  padding: 12px 14px;
  border-radius: var(--r-sm);
  border: 1px solid var(--glass-border);
  background: var(--layer2);
  color: var(--t1);
  font-size: 15px;
  outline: none;
  transition: border-color 0.25s var(--ease), box-shadow 0.25s var(--ease);
}

.field input::placeholder {
  color: var(--t4);
}

.field input:focus {
  border-color: rgba(245, 158, 11, 0.35);
  box-shadow: 0 0 0 3px var(--gold-dim);
}

.field input:disabled {
  opacity: 0.55;
}

.password-wrap {
  position: relative;
}

.password-wrap input {
  padding-right: 48px;
}

.toggle-vis {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 28px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--t3);
  transition: color 0.2s, background 0.2s;
}

.toggle-vis:hover {
  color: var(--t2);
  background: var(--glass-hover);
}

.error {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--danger-bg);
  color: var(--danger);
  font-size: 13px;
  line-height: 1.45;
}

.btn-primary {
  margin-top: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 13px 16px;
  border-radius: var(--r-md);
  background: var(--gold-grad);
  color: #fff;
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.01em;
  box-shadow: 0 4px 18px var(--gold-glow);
  transition: transform 0.25s var(--spring), box-shadow 0.25s var(--ease), opacity 0.2s;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px) scale(1.01);
  box-shadow: 0 8px 28px rgba(245, 158, 11, 0.35);
}

.btn-primary:active:not(:disabled) {
  transform: scale(0.98);
}

.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.btn-spin {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.btn-ghost {
  width: 100%;
  padding: 11px 16px;
  border-radius: var(--r-md);
  border: 1px solid var(--glass-border);
  background: transparent;
  color: var(--t2);
  font-size: 13px;
  font-weight: 500;
  transition: background 0.2s var(--ease), color 0.2s, border-color 0.2s;
}

.btn-ghost:hover:not(:disabled) {
  background: var(--glass-hover);
  color: var(--t1);
  border-color: rgba(255, 255, 255, 0.1);
}

.btn-ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.trust {
  margin: 2px 0 0;
  text-align: center;
  font-size: 11px;
  color: var(--t4);
  letter-spacing: 0.02em;
}

/* —— Dev hint —— */
.dev-hint {
  width: 100%;
  animation: rise 0.7s 0.22s var(--ease) both;
}

.dev-hint summary {
  cursor: pointer;
  text-align: center;
  font-size: 12px;
  color: var(--t4);
  list-style: none;
  user-select: none;
  transition: color 0.2s;
}

.dev-hint summary::-webkit-details-marker { display: none; }

.dev-hint summary:hover {
  color: var(--t3);
}

.dev-hint[open] summary {
  color: var(--t3);
  margin-bottom: 10px;
}

.dev-hint p {
  margin: 0;
  padding: 12px 14px;
  border-radius: var(--r-sm);
  background: var(--gold-dim);
  border: 1px solid rgba(245, 158, 11, 0.08);
  font-size: 12px;
  color: var(--t3);
  line-height: 1.65;
  text-align: center;
}

.dev-hint code {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--gold-text);
  background: rgba(245, 158, 11, 0.08);
  padding: 1px 5px;
  border-radius: 4px;
}

@media (max-width: 480px) {
  .brand-name { font-size: 30px; }
  .auth-panel { padding: 18px 16px 16px; }
  .login-page { padding: 24px 16px; }
}
</style>
