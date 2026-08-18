export const COMPANION_STATE_PRIORITY = Object.freeze({
  idle: 0,
  greeting: 10,
  listening: 20,
  thinking: 30,
  routed: 40,
  working: 50,
  celebrate: 60,
  confused: 70,
  error: 80,
  alert: 90
})

export function resolveCompanionState(signals, now = Date.now()) {
  let resolved = 'idle'

  for (const [state, signal] of signals.entries()) {
    if (!Object.hasOwn(COMPANION_STATE_PRIORITY, state) || signal.expiresAt <= now) continue
    if (COMPANION_STATE_PRIORITY[state] > COMPANION_STATE_PRIORITY[resolved]) {
      resolved = state
    }
  }

  return resolved
}

export function createCompanionSignalStore(clock = Date.now) {
  const signals = new Map()

  return {
    signal(state, { ttl } = {}) {
      if (!Object.hasOwn(COMPANION_STATE_PRIORITY, state) || state === 'idle') return
      signals.set(state, {
        expiresAt: Number.isFinite(ttl) ? clock() + Math.max(0, ttl) : Infinity
      })
    },
    clear(state) {
      signals.delete(state)
    },
    clearAll() {
      signals.clear()
    },
    current() {
      const now = clock()
      for (const [state, signal] of signals.entries()) {
        if (signal.expiresAt <= now) signals.delete(state)
      }
      return resolveCompanionState(signals, now)
    }
  }
}
