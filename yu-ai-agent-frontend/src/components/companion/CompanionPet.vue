<template>
  <div
    v-if="petEnabled"
    class="companion-pet"
    :class="{ collapsed, dragging }"
    :style="positionStyle"
  >
    <button
      v-if="collapsed"
      class="pet-tab"
      type="button"
      :aria-label="`展开${companionForm.displayName}`"
      @click="expand"
    >
      <span aria-hidden="true"></span>
    </button>

    <template v-else>
      <PetBubble :message="bubble" />

      <Transition name="pet-menu">
        <div
          v-if="menuOpen"
          ref="petMenu"
          class="pet-menu"
          role="menu"
          @keydown="handleMenuKeydown"
        >
          <button type="button" role="menuitem" @click="goToChat">继续对话</button>
          <button type="button" role="menuitem" @click="openSettings">伙伴设置</button>
          <button type="button" role="menuitem" @click="toggleWalk">{{ presence === 'away' ? '回来坐下' : '去门口转转' }}</button>
          <button type="button" role="menuitem" @click="mute">静音一小时</button>
          <button type="button" role="menuitem" @click="collapse">收起到边缘</button>
        </div>
      </Transition>

      <button
        ref="petButton"
        class="pet-button"
        type="button"
        :aria-label="`${companionForm.displayName}，${roomLabel}，当前状态：${stateLabel}`"
        :aria-expanded="menuOpen"
        @pointerdown="startDrag"
        @pointermove="moveDrag"
        @pointerup="endDrag"
        @pointercancel="endDrag"
        @click="handlePetClick"
        @contextmenu.prevent="menuOpen = true"
        @keydown.esc="menuOpen = false"
      >
        <PetRoom
          :presence="presence"
          :chair="companionForm.petWorld.chair"
          :rug="companionForm.petWorld.rug"
          :status-label="roomLabel"
          :travel="travel"
          @travel-end="onTravelEnd"
        >
          <PetStage
            :state="activeState"
            :motion="companionForm.petMotion"
            :skin="companionForm.petSkin"
            :presence="spritePresence"
            :travel="travel"
          />
        </PetRoom>
      </button>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { useCompanion } from '../../composables/useCompanion'
import PetBubble from './PetBubble.vue'
import PetRoom from './PetRoom.vue'
import PetStage from './PetStage.vue'

const LAYOUT_KEY = 'wp-companion-layout'
const COLLAPSED_KEY = 'wp-companion-collapsed'
const ROOM_WIDTH = 200
const ROOM_HEIGHT = 176
const VIEWPORT_GAP = 12

const router = useRouter()
const {
  activeState,
  bubble,
  companionForm,
  loadCompanion,
  muteFor,
  notify,
  petEnabled,
  presence,
  requestSettings,
  roomLabel,
  setPageHidden,
  tickPresence,
  togglePresence
} = useCompanion()

const petButton = ref(null)
const petMenu = ref(null)
const menuOpen = ref(false)
const dragging = ref(false)
const collapsed = ref(localStorage.getItem(COLLAPSED_KEY) === 'true')
const position = ref(readPosition())
const travel = ref('')
const spritePresence = ref(presence.value)
let dragStart = null
let moved = false
let suppressClick = false
let presenceTick = null
let travelTimer = null

const stateLabels = {
  idle: '待机',
  greeting: '向你打招呼',
  listening: '认真聆听',
  thinking: '正在思考',
  routed: '专家已就位',
  working: '正在工作',
  celebrate: '任务完成',
  confused: '等待补充信息',
  error: '连接异常',
  alert: '需要注意'
}

const stateLabel = computed(() => stateLabels[activeState.value] || '待机')
const positionStyle = computed(() => ({
  left: `${position.value.x}px`,
  top: `${position.value.y}px`
}))

function readPosition() {
  try {
    const saved = JSON.parse(localStorage.getItem(LAYOUT_KEY))
    if (Number.isFinite(saved?.x) && Number.isFinite(saved?.y)) return saved
  } catch {
    // Ignore invalid local preferences.
  }
  return {
    x: Math.max(VIEWPORT_GAP, window.innerWidth - ROOM_WIDTH - 24),
    y: Math.max(VIEWPORT_GAP, window.innerHeight - ROOM_HEIGHT - 84)
  }
}

function clampPosition(next = position.value) {
  position.value = {
    x: Math.min(Math.max(VIEWPORT_GAP, next.x), Math.max(VIEWPORT_GAP, window.innerWidth - ROOM_WIDTH - VIEWPORT_GAP)),
    y: Math.min(Math.max(VIEWPORT_GAP, next.y), Math.max(VIEWPORT_GAP, window.innerHeight - ROOM_HEIGHT - VIEWPORT_GAP))
  }
}

function savePosition() {
  localStorage.setItem(LAYOUT_KEY, JSON.stringify(position.value))
}

function handleResize() {
  clampPosition()
}

function startDrag(event) {
  if (event.button !== 0) return
  petButton.value?.setPointerCapture(event.pointerId)
  dragStart = {
    pointerX: event.clientX,
    pointerY: event.clientY,
    x: position.value.x,
    y: position.value.y
  }
  moved = false
  dragging.value = true
}

function moveDrag(event) {
  if (!dragStart || !dragging.value) return
  const dx = event.clientX - dragStart.pointerX
  const dy = event.clientY - dragStart.pointerY
  if (Math.abs(dx) + Math.abs(dy) > 5) moved = true
  clampPosition({ x: dragStart.x + dx, y: dragStart.y + dy })
}

function endDrag(event) {
  if (!dragStart) return
  if (petButton.value?.hasPointerCapture(event.pointerId)) {
    petButton.value.releasePointerCapture(event.pointerId)
  }
  dragging.value = false
  dragStart = null
  if (moved) {
    savePosition()
    suppressClick = true
  }
}

function handlePetClick() {
  if (suppressClick) {
    suppressClick = false
    return
  }
  menuOpen.value = false
  togglePresence()
}

function toggleWalk() {
  menuOpen.value = false
  togglePresence()
}

function handleMenuKeydown(event) {
  const items = [...petMenu.value.querySelectorAll('[role="menuitem"]')]
  const currentIndex = items.indexOf(document.activeElement)
  if (event.key === 'Escape') {
    event.preventDefault()
    menuOpen.value = false
    petButton.value?.focus()
    return
  }
  if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
  event.preventDefault()
  const direction = event.key === 'ArrowDown' ? 1 : -1
  const nextIndex = (currentIndex + direction + items.length) % items.length
  items[nextIndex]?.focus()
}

function goToChat() {
  menuOpen.value = false
  if (router.currentRoute.value.path !== '/chat/career') router.push('/chat/career')
}

function openSettings() {
  menuOpen.value = false
  requestSettings()
  if (router.currentRoute.value.path !== '/chat/career') router.push('/chat/career')
}

function mute() {
  muteFor()
  menuOpen.value = false
}

function collapse() {
  collapsed.value = true
  menuOpen.value = false
  localStorage.setItem(COLLAPSED_KEY, 'true')
}

function expand() {
  collapsed.value = false
  localStorage.setItem(COLLAPSED_KEY, 'false')
}

function greetOnceToday() {
  const today = new Date().toISOString().slice(0, 10)
  if (localStorage.getItem('wp-companion-greeted') === today) return
  localStorage.setItem('wp-companion-greeted', today)
  notify('greeting', {
    ttl: 1800,
    message: `我是${companionForm.value.displayName}，今天也一起向目标前进吧。`
  })
}

watch(presence, (next, prev) => {
  window.clearTimeout(travelTimer)
  const reduceMotion = typeof window !== 'undefined'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (!prev || reduceMotion || companionForm.value.petMotion === 'off') {
    travel.value = ''
    spritePresence.value = next
    return
  }
  travel.value = next === 'away' ? 'out' : 'in'
  spritePresence.value = 'away'
  travelTimer = window.setTimeout(() => onTravelEnd(), 2200)
}, { flush: 'sync' })

function onTravelEnd() {
  window.clearTimeout(travelTimer)
  travelTimer = null
  travel.value = ''
  spritePresence.value = presence.value
}

watch(menuOpen, async open => {
  if (!open) return
  await nextTick()
  petMenu.value?.querySelector('[role="menuitem"]')?.focus()
})

function handleVisibility() {
  setPageHidden(document.hidden)
}

onMounted(async () => {
  clampPosition()
  window.addEventListener('resize', handleResize)
  document.addEventListener('visibilitychange', handleVisibility)
  presenceTick = window.setInterval(() => tickPresence(), 5000)
  await loadCompanion()
  greetOnceToday()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('visibilitychange', handleVisibility)
  if (presenceTick) window.clearInterval(presenceTick)
  window.clearTimeout(travelTimer)
})
</script>

<style scoped>
.companion-pet {
  position: fixed;
  z-index: 150;
  width: 200px;
  height: 176px;
  user-select: none;
  touch-action: none;
}

.pet-button {
  width: 200px;
  height: 176px;
  padding: 0;
  border: 0;
  border-radius: 22px;
  background: transparent;
  cursor: grab;
  filter: drop-shadow(var(--pet-widget-shadow));
}

.pet-button:active,
.dragging .pet-button {
  cursor: grabbing;
}

.pet-button:focus-visible,
.pet-tab:focus-visible {
  outline: 3px solid var(--gold);
  outline-offset: 2px;
}

.pet-menu {
  position: absolute;
  right: 8px;
  bottom: calc(100% + 8px);
  display: grid;
  width: 132px;
  padding: 6px;
  border: 1px solid var(--glass-border);
  border-radius: 12px;
  background: var(--layer1);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.18);
}

.pet-menu button {
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--t2);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.pet-menu button:hover,
.pet-menu button:focus-visible {
  background: var(--gold-soft);
  color: var(--gold-text);
  outline: none;
}

.pet-menu-enter-active,
.pet-menu-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
  transform-origin: right bottom;
}

.pet-menu-enter-from,
.pet-menu-leave-to {
  opacity: 0;
  transform: translateY(5px) scale(0.96);
}

.companion-pet.collapsed {
  width: 34px;
  height: 52px;
}

.pet-tab {
  width: 34px;
  height: 52px;
  padding: 0;
  border: 1px solid var(--gold-border-soft);
  border-radius: 18px 0 0 18px;
  background: var(--gold-soft);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  cursor: pointer;
}

.pet-tab span {
  display: block;
  width: 14px;
  height: 14px;
  margin: auto;
  border: 3px solid var(--gold);
  border-radius: 50%;
  background: var(--layer1);
}

@media (max-width: 768px) {
  .companion-pet {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pet-menu-enter-active,
  .pet-menu-leave-active {
    transition: none;
  }
}
</style>
