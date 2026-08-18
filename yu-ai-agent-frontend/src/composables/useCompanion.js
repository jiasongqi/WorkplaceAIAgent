import { computed, ref } from 'vue'

import { getMyCompanion, updateMyCompanion } from '../api'
import {
  buildPetPrefs,
  hydrateCompanionForm,
  resolvePresence,
  roomStatusLabel,
  setPresence
} from '../companion/catalog'
import { createCompanionSignalStore } from './companionState'

const PERSISTENT_STATES = ['listening', 'thinking', 'working']
const companionForm = ref(hydrateCompanionForm())
const activeState = ref('idle')
const bubble = ref('')
const companionSaving = ref(false)
const companionMsg = ref('')
const settingsRequested = ref(false)
const storedMutedUntil = Number(localStorage.getItem('wp-companion-muted-until'))
const mutedUntil = ref(Number.isFinite(storedMutedUntil) ? storedMutedUntil : 0)
const pageHidden = ref(typeof document !== 'undefined' && document.hidden)
const manualAway = ref(false)
const signalStore = createCompanionSignalStore()
let loadPromise = null
let bubbleTimer = null
const stateTimers = new Set()

const petEnabled = computed(() => companionForm.value.petEnabled !== false)
const bubblesEnabled = computed(() =>
  petEnabled.value && companionForm.value.petBubbleLevel !== 'none' && Date.now() >= mutedUntil.value
)
const presence = computed(() => resolvePresence({
  pageHidden: pageHidden.value,
  manualAway: manualAway.value
}))
const roomLabel = computed(() => roomStatusLabel(activeState.value, presence.value))

function syncWorldPresence(nextPresence) {
  if (companionForm.value.petWorld?.presence === nextPresence) return
  companionForm.value = {
    ...companionForm.value,
    petWorld: setPresence(companionForm.value.petWorld, nextPresence)
  }
}

function refreshState() {
  activeState.value = signalStore.current()
  syncWorldPresence(presence.value)
}

function scheduleRefresh(ttl) {
  if (!Number.isFinite(ttl)) return
  const timer = window.setTimeout(() => {
    stateTimers.delete(timer)
    refreshState()
  }, Math.max(0, ttl) + 20)
  stateTimers.add(timer)
}

function showBubble(message, ttl = 2800) {
  if (!message || !bubblesEnabled.value) return
  bubble.value = message
  if (bubbleTimer) window.clearTimeout(bubbleTimer)
  bubbleTimer = window.setTimeout(() => {
    bubble.value = ''
    bubbleTimer = null
  }, ttl)
}

function notify(state, { ttl, message, persistent = false } = {}) {
  if (persistent) {
    PERSISTENT_STATES.forEach(signal => signalStore.clear(signal))
  }
  signalStore.signal(state, { ttl })
  refreshState()
  scheduleRefresh(ttl)
  showBubble(message, ttl ?? 2800)
}

function setActivity(state = 'idle', message) {
  PERSISTENT_STATES.forEach(signal => signalStore.clear(signal))
  if (state !== 'idle') signalStore.signal(state)
  refreshState()
  showBubble(message)
}

function resetCompanion() {
  signalStore.clearAll()
  refreshState()
}

function mapCompanion(data) {
  companionForm.value = hydrateCompanionForm(data)
}

async function loadCompanion() {
  if (loadPromise) return loadPromise
  if (!localStorage.getItem('token')) return companionForm.value

  loadPromise = getMyCompanion()
    .then(res => {
      const data = res.data?.data
      if (data) mapCompanion(data)
      return companionForm.value
    })
    .catch(error => {
      console.warn('load companion failed', error)
      return companionForm.value
    })
    .finally(() => {
      loadPromise = null
    })
  return loadPromise
}

async function saveCompanion() {
  companionSaving.value = true
  companionMsg.value = ''
  try {
    await updateMyCompanion({
      displayName: companionForm.value.displayName,
      personaPrompt: companionForm.value.personaPrompt,
      stylePrefs: {
        tone: companionForm.value.tone,
        focus: companionForm.value.focus,
        pet: buildPetPrefs(companionForm.value)
      }
    })
    companionMsg.value = '已保存，下一轮对话生效'
  } catch (error) {
    companionMsg.value = error.message || '保存失败'
  } finally {
    companionSaving.value = false
  }
}

function muteFor(durationMs = 60 * 60 * 1000) {
  mutedUntil.value = Date.now() + durationMs
  localStorage.setItem('wp-companion-muted-until', String(mutedUntil.value))
  bubble.value = ''
}

function setPageHidden(hidden) {
  const wasHidden = pageHidden.value
  pageHidden.value = hidden
  if (!hidden && wasHidden) {
    manualAway.value = false
    syncWorldPresence('onChair')
    showBubble('我回来陪你坐着。')
    return
  }
  syncWorldPresence(hidden || manualAway.value ? 'away' : 'onChair')
  if (hidden && !wasHidden) showBubble('你先忙，我出去转转。')
}

function tickPresence() {
  setPageHidden(typeof document !== 'undefined' && document.hidden)
}

function sendAway() {
  if (presence.value === 'away') return
  manualAway.value = true
  syncWorldPresence('away')
  showBubble('我去门口转转。')
}

function recallCompanion() {
  manualAway.value = false
  pageHidden.value = false
  syncWorldPresence('onChair')
  showBubble('我回来陪你坐着。')
}

function togglePresence() {
  if (presence.value === 'away') recallCompanion()
  else sendAway()
}

function requestSettings() {
  settingsRequested.value = true
}

function consumeSettingsRequest() {
  settingsRequested.value = false
}

export function useCompanion() {
  return {
    activeState,
    bubble,
    bubblesEnabled,
    companionForm,
    companionMsg,
    companionSaving,
    petEnabled,
    presence,
    roomLabel,
    settingsRequested,
    consumeSettingsRequest,
    loadCompanion,
    muteFor,
    notify,
    recallCompanion,
    requestSettings,
    resetCompanion,
    saveCompanion,
    sendAway,
    setActivity,
    setPageHidden,
    showBubble,
    tickPresence,
    togglePresence
  }
}
