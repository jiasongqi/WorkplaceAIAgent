import test from 'node:test'
import assert from 'node:assert/strict'

import {
  COMPANION_STATE_PRIORITY,
  createCompanionSignalStore,
  resolveCompanionState
} from './companionState.js'

test('resolves the highest-priority active companion signal', () => {
  const now = 1_000
  const signals = new Map([
    ['thinking', { expiresAt: Infinity }],
    ['confused', { expiresAt: now + 500 }]
  ])

  assert.equal(resolveCompanionState(signals, now), 'confused')
  assert.ok(COMPANION_STATE_PRIORITY.confused > COMPANION_STATE_PRIORITY.thinking)
})

test('falls back after a temporary signal expires', () => {
  const signals = new Map([
    ['thinking', { expiresAt: Infinity }],
    ['celebrate', { expiresAt: 1_500 }]
  ])

  assert.equal(resolveCompanionState(signals, 1_000), 'celebrate')
  assert.equal(resolveCompanionState(signals, 2_000), 'thinking')
})

test('signal store clears persistent activity without removing temporary reactions', () => {
  let now = 1_000
  const store = createCompanionSignalStore(() => now)

  store.signal('working')
  store.signal('routed', { ttl: 600 })
  store.clear('working')

  assert.equal(store.current(), 'routed')
  now = 2_000
  assert.equal(store.current(), 'idle')
})
