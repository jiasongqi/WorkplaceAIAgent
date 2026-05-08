<template>
  <div class="home-container">
    <div class="header">
      <div class="glitch-wrapper">
        <h1 class="glitch-title">职场生存智囊</h1>
      </div>
      <p class="subtitle">/ 全场景职场 AI 决策系统 /</p>
      <div class="cyber-line"></div>
    </div>

    <div class="apps-container">
      <div class="app-card" @click="navigateTo('/career-advisor')">
        <div class="card-glow"></div>
        <div class="app-icon career-icon">💼</div>
        <div class="app-info">
          <div class="app-title">职场顾问</div>
          <div class="app-desc">智能路由 · 简历优化 · 薪资谈判 · 离职规划</div>
          <div class="app-tags">
            <span class="tag">简历</span>
            <span class="tag">谈薪</span>
            <span class="tag">离职</span>
            <span class="tag">晋升</span>
          </div>
        </div>
        <div class="app-button">
          <span class="btn-text">立即咨询</span>
          <span class="btn-icon">→</span>
        </div>
      </div>

      <div class="app-card" @click="navigateTo('/super-agent')">
        <div class="card-glow"></div>
        <div class="app-icon robot-icon">🤖</div>
        <div class="app-info">
          <div class="app-title">超级智能体</div>
          <div class="app-desc">多步推理 · 工具调用 · 自主规划 · 文件生成</div>
          <div class="app-tags">
            <span class="tag">搜索</span>
            <span class="tag">PDF</span>
            <span class="tag">代码</span>
            <span class="tag">文件</span>
          </div>
        </div>
        <div class="app-button">
          <span class="btn-text">立即体验</span>
          <span class="btn-icon">→</span>
        </div>
      </div>

      <div class="app-card" @click="navigateTo('/love-master')">
        <div class="card-glow"></div>
        <div class="app-icon love-icon">💘</div>
        <div class="app-info">
          <div class="app-title">恋爱大师</div>
          <div class="app-desc">情感分析 · 恋爱话术 · 关系修复 · 脱单攻略</div>
          <div class="app-tags">
            <span class="tag tag-pink">表白</span>
            <span class="tag tag-pink">挽回</span>
            <span class="tag tag-pink">约会</span>
            <span class="tag tag-pink">情感</span>
          </div>
        </div>
        <div class="app-button app-button-pink">
          <span class="btn-text">立即咨询</span>
          <span class="btn-icon">→</span>
        </div>
      </div>
    </div>

    <div class="cyber-circles">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>

    <AppFooter />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import AppFooter from '../components/AppFooter.vue'

useHead({
  title: '职场生存智囊 - 全场景职场 AI 决策系统',
  meta: [{ name: 'description', content: '融合长链推理与多 Agent 协作的职场智能决策系统' }]
})

const router = useRouter()
const navigateTo = (path) => router.push(path)
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@400;500;700&display=swap');

:root {
  --neon-blue: #00f0ff;
  --neon-purple: #9000ff;
  --neon-green: #00ff88;
  --cyber-dark: #0a0f1e;
}

.home-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #0a0f1e;
  background-image:
    linear-gradient(0deg, rgba(8, 17, 34, 0.95), rgba(5, 8, 20, 0.95)),
    url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect x="0" y="0" width="100" height="1" fill="%23111133" opacity="0.3"/><rect x="0" y="0" width="1" height="100" fill="%23111133" opacity="0.3"/></svg>');
  background-size: auto, 40px 40px;
  position: relative;
  overflow: hidden;
}

.header {
  padding: 70px 20px 50px;
  text-align: center;
  position: relative;
  z-index: 2;
}

.glitch-wrapper { position: relative; display: inline-block; margin-bottom: 20px; }

.glitch-title {
  font-family: 'Orbitron', sans-serif;
  font-size: 3.2rem;
  font-weight: 700;
  color: #edf7ff;
  text-shadow: 0 0 5px rgba(0, 240, 255, 0.7), 0 0 20px rgba(0, 240, 255, 0.3);
  letter-spacing: 2px;
  animation: glitch 3s infinite;
}

.subtitle {
  font-family: 'Orbitron', sans-serif;
  font-size: 1.1rem;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 3px;
  margin: 0 auto 20px;
}

.cyber-line {
  height: 2px;
  width: 80%;
  max-width: 600px;
  margin: 0 auto;
  background: linear-gradient(90deg, transparent, #00f0ff, transparent);
}

.apps-container {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 50px;
  max-width: 1200px;
  margin: 60px auto;
  padding: 0 20px;
  flex: 1;
  position: relative;
  z-index: 2;
}

.app-card {
  width: 360px;
  background-color: rgba(17, 23, 41, 0.8);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 240, 255, 0.15), inset 0 0 0 1px rgba(255, 255, 255, 0.08);
  padding: 32px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.card-glow {
  position: absolute;
  top: -50%; left: -50%;
  width: 200%; height: 200%;
  background: radial-gradient(circle at center, rgba(0, 240, 255, 0.08) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.5s;
  pointer-events: none;
}

.app-card:hover {
  transform: translateY(-12px) scale(1.02);
  box-shadow: 0 20px 60px rgba(0, 240, 255, 0.25), inset 0 0 0 1px rgba(0, 240, 255, 0.4);
}

.app-card:hover .card-glow { opacity: 1; }

.app-icon {
  font-size: 3.5rem;
  margin-bottom: 20px;
  width: 80px; height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.career-icon {
  background: linear-gradient(135deg, #0088ff, #00d4ff);
  box-shadow: 0 0 20px rgba(0, 136, 255, 0.5);
}

.robot-icon {
  background: linear-gradient(135deg, #7c3aed, #4f56ff);
  box-shadow: 0 0 20px rgba(124, 58, 237, 0.5);
}

.love-icon {
  background: linear-gradient(135deg, #ff6b8b, #ff4d6d);
  box-shadow: 0 0 20px rgba(255, 107, 139, 0.5);
}

.app-info { text-align: center; margin-bottom: 24px; width: 100%; }

.app-title {
  font-family: 'Orbitron', sans-serif;
  font-size: 1.5rem;
  font-weight: bold;
  color: white;
  margin-bottom: 10px;
  text-shadow: 0 0 10px rgba(0, 240, 255, 0.4);
}

.app-desc { font-size: 0.95rem; color: rgba(255, 255, 255, 0.65); line-height: 1.6; margin-bottom: 12px; }

.app-tags { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }

.tag {
  background: rgba(0, 240, 255, 0.1);
  border: 1px solid rgba(0, 240, 255, 0.3);
  color: #00f0ff;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 0.78rem;
}

.tag-pink {
  background: rgba(255, 107, 139, 0.1);
  border: 1px solid rgba(255, 107, 139, 0.35);
  color: #ff8fab;
}

.app-button-pink {
  background: linear-gradient(90deg, #ff6b8b, #ff4d6d);
  border-color: rgba(255, 107, 139, 0.4);
}

.app-button-pink:hover { box-shadow: 0 0 20px rgba(255, 77, 109, 0.6); }

.app-button {
  background: linear-gradient(90deg, #0088ff, #00b2ff);
  color: white;
  padding: 12px 28px;
  border-radius: 30px;
  font-weight: 500;
  display: flex;
  align-items: center;
  border: 1px solid rgba(0, 240, 255, 0.3);
  transition: all 0.3s;
}

.app-button:hover { box-shadow: 0 0 20px rgba(0, 178, 255, 0.6); transform: scale(1.05); }

.btn-text { margin-right: 8px; letter-spacing: 1px; }
.btn-icon { font-size: 1.2rem; transition: transform 0.3s; }
.app-button:hover .btn-icon { transform: translateX(4px); }

.cyber-circles { position: absolute; top: 0; left: 0; width: 100%; height: 100%; overflow: hidden; z-index: 1; }

.circle { position: absolute; border-radius: 50%; opacity: 0.12; }
.circle-1 { width: 300px; height: 300px; top: -100px; right: -100px; background: linear-gradient(135deg, #00f0ff, #9000ff); animation: float 15s infinite alternate; }
.circle-2 { width: 500px; height: 500px; bottom: -200px; left: -200px; background: linear-gradient(135deg, #9000ff, #00ff88); animation: float 20s infinite alternate-reverse; }
.circle-3 { width: 200px; height: 200px; top: 40%; right: 15%; background: linear-gradient(135deg, #00ff88, #00f0ff); animation: float 12s infinite alternate; }

@keyframes float {
  0% { transform: translate(0, 0) rotate(0deg); }
  100% { transform: translate(50px, 50px) rotate(10deg); }
}

@keyframes glitch {
  0%, 100% { text-shadow: 0 0 5px rgba(0, 240, 255, 0.7), 0 0 10px rgba(0, 240, 255, 0.5); }
  50% { text-shadow: 0 0 5px rgba(0, 240, 255, 0.7), 0 0 20px rgba(0, 240, 255, 0.3), 0 0 40px rgba(0, 240, 255, 0.1); }
}

@media (max-width: 768px) {
  .glitch-title { font-size: 2.2rem; }
  .apps-container { gap: 24px; margin: 40px auto; }
  .app-card { width: 100%; max-width: 420px; }
}
</style>
