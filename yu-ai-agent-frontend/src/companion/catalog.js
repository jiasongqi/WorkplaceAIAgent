export const DEFAULT_SKIN_ID = 'cat'

export const DEFAULT_WORLD = Object.freeze({
  presence: 'onChair',
  chair: 'wood',
  rug: 'plain',
  gifts: Object.freeze([])
})

export const PET_SKINS = Object.freeze([
  {
    id: 'cat',
    name: '小猫咪',
    blurb: '会坐在椅子上陪你工作',
    selectable: true
  },
  {
    id: 'pilot',
    name: '小领航员',
    blurb: '职场搭档，擅长盯进度',
    selectable: true
  },
  {
    id: 'panda',
    name: '小熊猫',
    blurb: '房间装饰开放后解锁',
    selectable: false
  }
])

const PRESENCE = new Set(['onChair', 'away'])
const MAX_GIFTS = 50

const ROOM_STATUS_LABELS = Object.freeze({
  idle: '陪你坐着',
  greeting: '向你打招呼',
  listening: '认真聆听',
  thinking: '正在思考',
  routed: '专家已就位',
  working: '正在工作',
  celebrate: '任务完成',
  confused: '等待补充信息',
  error: '连接异常',
  alert: '需要注意'
})

export const DEFAULT_COMPANION_FORM = Object.freeze({
  displayName: '你的职场伙伴',
  tone: '简洁直接',
  focus: '',
  personaPrompt: '',
  petEnabled: true,
  petSkin: DEFAULT_SKIN_ID,
  petMotion: 'full',
  petBubbleLevel: 'key',
  petWorld: DEFAULT_WORLD
})

export function listSelectableSkins() {
  return PET_SKINS.filter(skin => skin.selectable)
}

export function resolveSkinId(skinId) {
  const match = PET_SKINS.find(skin => skin.id === skinId)
  if (match?.selectable) return match.id
  return DEFAULT_SKIN_ID
}

export function getSkin(skinId) {
  const id = resolveSkinId(skinId)
  return PET_SKINS.find(skin => skin.id === id)
}

export function normalizeWorld(world) {
  const gifts = []
  const seen = new Set()
  if (Array.isArray(world?.gifts)) {
    for (const gift of world.gifts) {
      if (!gift || typeof gift !== 'object' || typeof gift.id !== 'string' || !gift.id || seen.has(gift.id)) continue
      seen.add(gift.id)
      gifts.push({ ...gift })
      if (gifts.length >= MAX_GIFTS) break
    }
  }
  return {
    presence: PRESENCE.has(world?.presence) ? world.presence : DEFAULT_WORLD.presence,
    chair: typeof world?.chair === 'string' && world.chair ? world.chair : DEFAULT_WORLD.chair,
    rug: typeof world?.rug === 'string' && world.rug ? world.rug : DEFAULT_WORLD.rug,
    gifts
  }
}

export function setPresence(world, presence) {
  return normalizeWorld({ ...world, presence })
}

export function resolvePresence({ pageHidden = false, manualAway = false } = {}) {
  return pageHidden || manualAway ? 'away' : 'onChair'
}

export function roomStatusLabel(state, presence) {
  if (presence === 'away') return '出去了'
  return ROOM_STATUS_LABELS[state] || ROOM_STATUS_LABELS.idle
}

export function resolveCatPose(state, presence) {
  if (presence === 'away') return 'walk'
  if (state === 'celebrate') return 'celebrate'
  if (state === 'greeting') return 'greeting'
  if (state === 'thinking' || state === 'listening') return 'thinking'
  if (state === 'working' || state === 'routed') return 'snack'
  return 'idle'
}

export function addGift(world, gift) {
  const next = normalizeWorld(world)
  if (!gift?.id || next.gifts.length >= MAX_GIFTS) return next
  if (next.gifts.some(item => item?.id === gift.id)) return next
  return {
    ...next,
    gifts: [...next.gifts, { ...gift }]
  }
}

export function buildPetPrefs(form) {
  return {
    enabled: form.petEnabled !== false,
    skin: resolveSkinId(form.petSkin),
    motion: form.petMotion || 'full',
    bubbleLevel: form.petBubbleLevel || 'key',
    world: normalizeWorld(form.petWorld)
  }
}

export function hydrateCompanionForm(data) {
  const pet = data?.stylePrefs?.pet || {}
  return {
    displayName: data?.displayName || DEFAULT_COMPANION_FORM.displayName,
    tone: data?.stylePrefs?.tone || DEFAULT_COMPANION_FORM.tone,
    focus: data?.stylePrefs?.focus || '',
    personaPrompt: data?.personaPrompt || '',
    petEnabled: pet.enabled ?? true,
    petSkin: resolveSkinId(pet.skin || DEFAULT_SKIN_ID),
    petMotion: pet.motion || 'full',
    petBubbleLevel: pet.bubbleLevel || 'key',
    petWorld: normalizeWorld(pet.world)
  }
}
