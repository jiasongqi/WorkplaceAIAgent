import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DEFAULT_COMPANION_FORM,
  DEFAULT_SKIN_ID,
  DEFAULT_WORLD,
  addGift,
  buildPetPrefs,
  hydrateCompanionForm,
  listSelectableSkins,
  normalizeWorld,
  resolveCatPose,
  resolvePresence,
  resolveSkinId,
  roomStatusLabel,
  setPresence
} from './catalog.js'

test('selectable skins include cat and pilot, but not coming-soon panda', () => {
  const ids = listSelectableSkins().map(skin => skin.id)
  assert.deepEqual(ids, ['cat', 'pilot'])
})

test('unknown or locked skins fall back to the default cat', () => {
  assert.equal(DEFAULT_SKIN_ID, 'cat')
  assert.equal(resolveSkinId('cat'), 'cat')
  assert.equal(resolveSkinId('pilot'), 'pilot')
  assert.equal(resolveSkinId('panda'), 'cat')
  assert.equal(resolveSkinId('unknown'), 'cat')
  assert.equal(resolveSkinId(''), 'cat')
})

test('world defaults keep later room and gift slots without inventing gifts', () => {
  assert.deepEqual(normalizeWorld(undefined), {
    presence: 'onChair',
    chair: 'wood',
    rug: 'plain',
    gifts: []
  })
  assert.deepEqual(normalizeWorld({ presence: 'away', chair: 'velvet', gifts: [{ id: 'yarn' }] }), {
    presence: 'away',
    chair: 'velvet',
    rug: 'plain',
    gifts: [{ id: 'yarn' }]
  })
  assert.equal(normalizeWorld({ presence: 'flying' }).presence, 'onChair')
})

test('world gifts are copied so later inventory updates stay local', () => {
  const gifts = [{ id: 'yarn' }]
  const world = normalizeWorld({ gifts })
  world.gifts.push({ id: 'bell' })
  assert.deepEqual(gifts, [{ id: 'yarn' }])
})

test('saved pet prefs keep skin plus forward-compatible world', () => {
  const prefs = buildPetPrefs({
    petEnabled: true,
    petSkin: 'pilot',
    petMotion: 'reduced',
    petBubbleLevel: 'key',
    petWorld: { presence: 'onChair', chair: 'wood', rug: 'plain', gifts: [] }
  })

  assert.equal(prefs.skin, 'pilot')
  assert.deepEqual(prefs.world, {
    presence: 'onChair',
    chair: 'wood',
    rug: 'plain',
    gifts: []
  })
})

test('hydrates companion form from saved prefs and falls locked skins back to cat', () => {
  const form = hydrateCompanionForm({
    displayName: '小橘',
    personaPrompt: '先给结论',
    stylePrefs: {
      tone: '温柔',
      focus: '简历',
      pet: {
        enabled: true,
        skin: 'panda',
        motion: 'reduced',
        bubbleLevel: 'all'
      }
    }
  })

  assert.equal(form.displayName, '小橘')
  assert.equal(form.tone, '温柔')
  assert.equal(form.petSkin, 'cat')
  assert.equal(form.petMotion, 'reduced')
  assert.deepEqual(form.petWorld, {
    presence: 'onChair',
    chair: 'wood',
    rug: 'plain',
    gifts: []
  })
  assert.notEqual(form.petWorld, DEFAULT_WORLD)
  form.petWorld.gifts.push({ id: 'yarn' })
  assert.equal(DEFAULT_WORLD.gifts.length, 0)
})

test('missing companion data uses the later-ready cat defaults', () => {
  assert.equal(DEFAULT_COMPANION_FORM.petSkin, 'cat')
  assert.deepEqual(hydrateCompanionForm(undefined), {
    displayName: '你的职场伙伴',
    tone: '简洁直接',
    focus: '',
    personaPrompt: '',
    petEnabled: true,
    petSkin: 'cat',
    petMotion: 'full',
    petBubbleLevel: 'key',
    petWorld: {
      presence: 'onChair',
      chair: 'wood',
      rug: 'plain',
      gifts: []
    }
  })
})

test('world helpers keep room slots and do not duplicate gifts', () => {
  const away = setPresence(DEFAULT_WORLD, 'away')
  assert.equal(away.presence, 'away')
  assert.equal(away.chair, 'wood')

  const invalid = setPresence(DEFAULT_WORLD, 'flying')
  assert.equal(invalid.presence, 'onChair')

  const withYarn = addGift(DEFAULT_WORLD, { id: 'yarn', name: '毛线球' })
  const duplicate = addGift(withYarn, { id: 'yarn', name: '另一个毛线球' })
  assert.deepEqual(duplicate.gifts, [{ id: 'yarn', name: '毛线球' }])
  assert.equal(addGift(DEFAULT_WORLD, { name: '无名礼物' }).gifts.length, 0)
})

test('world gift inventory drops invalid items and caps at 50', () => {
  const overflow = Array.from({ length: 60 }, (_, index) => ({ id: `gift-${index}` }))
  const world = normalizeWorld({ gifts: [{ name: 'no-id' }, ...overflow, overflow[0]] })
  assert.equal(world.gifts.length, 50)
  assert.equal(world.gifts[0].id, 'gift-0')
  assert.equal(world.gifts.at(-1).id, 'gift-49')
})

test('cat sits while you are here and walks away when sent out or the page is hidden', () => {
  assert.equal(resolvePresence({ pageHidden: false }), 'onChair')
  assert.equal(resolvePresence({ pageHidden: false, idleMs: 120_000, state: 'idle' }), 'onChair')
  assert.equal(resolvePresence({ pageHidden: false, manualAway: true }), 'away')
  assert.equal(resolvePresence({ pageHidden: true }), 'away')
  assert.equal(resolvePresence({ pageHidden: true, state: 'working' }), 'away')
})

test('room status makes sitting versus away readable in one glance', () => {
  assert.equal(roomStatusLabel('thinking', 'onChair'), '正在思考')
  assert.equal(roomStatusLabel('working', 'onChair'), '正在工作')
  assert.equal(roomStatusLabel('idle', 'onChair'), '陪你坐着')
  assert.equal(roomStatusLabel('idle', 'away'), '出去了')
  assert.equal(roomStatusLabel('thinking', 'away'), '出去了')
})

test('cat pose uses V5 sit, V8 celebrate, V7 snack, and walk when away', () => {
  assert.equal(resolveCatPose('idle', 'onChair'), 'idle')
  assert.equal(resolveCatPose('greeting', 'onChair'), 'greeting')
  assert.equal(resolveCatPose('thinking', 'onChair'), 'thinking')
  assert.equal(resolveCatPose('listening', 'onChair'), 'thinking')
  assert.equal(resolveCatPose('working', 'onChair'), 'snack')
  assert.equal(resolveCatPose('routed', 'onChair'), 'snack')
  assert.equal(resolveCatPose('celebrate', 'onChair'), 'celebrate')
  assert.equal(resolveCatPose('confused', 'onChair'), 'idle')
  assert.equal(resolveCatPose('celebrate', 'away'), 'walk')
  assert.equal(resolveCatPose('working', 'away'), 'walk')
  assert.equal(resolveCatPose(undefined, undefined), 'idle')
})
