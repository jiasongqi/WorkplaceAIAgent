export const CHAIR_POSE = Object.freeze({ x: 0, y: 0 })
export const DOOR_POSE = Object.freeze({ x: 78, y: 12 })

const STYLES = ['scamper', 'pounce', 'zigzag', 'waddle', 'dash', 'nosey']

function lerp(a, b, t) {
  return a + (b - a) * t
}

function rand(rng, min, max) {
  return min + rng() * (max - min)
}

function pick(rng, list) {
  return list[Math.min(list.length - 1, Math.floor(rng() * list.length))]
}

export function poseTransform({ x, y }) {
  return `translate3d(${Math.round(x)}px, ${Math.round(y)}px, 0)`
}

function hopCount(style, rng) {
  if (style === 'dash') return 2 + Math.floor(rng() * 2)
  if (style === 'pounce') return 2 + Math.floor(rng() * 2)
  if (style === 'scamper') return 5 + Math.floor(rng() * 3)
  if (style === 'zigzag') return 4 + Math.floor(rng() * 3)
  if (style === 'nosey') return 4 + Math.floor(rng() * 2)
  return 3 + Math.floor(rng() * 3)
}

function hopHeight(style, rng, index, total) {
  if (style === 'pounce' && index === 0) return rand(rng, 18, 28)
  if (style === 'dash') return rand(rng, 6, 12)
  if (style === 'scamper') return rand(rng, 5, 13)
  if (style === 'waddle') return rand(rng, 8, 16)
  if (index === total - 1) return rand(rng, 5, 10)
  return rand(rng, 8, 20)
}

function splitProgress(count, style, rng) {
  const weights = Array.from({ length: count }, (_, index) => {
    if (style === 'pounce' && index === 0) return 1.6 + rng()
    if (style === 'dash' && index === 0) return 1.2 + rng()
    if (style === 'waddle' && rng() < 0.3) return 0.35 + rng() * 0.3
    return 0.55 + rng()
  })
  const sum = weights.reduce((total, value) => total + value, 0)
  return weights.map(value => value / sum)
}

function clampToward(from, to, value, slack = 10) {
  const lo = Math.min(from, to) - slack
  const hi = Math.max(from, to) + slack
  return Math.max(lo, Math.min(hi, value))
}

export function planHopTrip(from, to, rng = Math.random) {
  const style = pick(rng, STYLES)
  const hops = hopCount(style, rng)
  const slices = splitProgress(hops, style, rng)
  const dirX = Math.sign(to.x - from.x) || 1
  const keyframes = [{
    transform: poseTransform(from),
    easing: 'cubic-bezier(0.2, 0.8, 0.25, 1)'
  }]

  let progress = 0
  let x = from.x
  let y = from.y
  let zigzag = rng() < 0.5 ? 1 : -1

  if (rng() < 0.28) {
    keyframes.push({
      transform: poseTransform({ x: from.x + rand(rng, -4, 6), y: from.y + rand(rng, -10, -4) }),
      easing: 'cubic-bezier(0.3, 0.1, 0.3, 1)'
    })
    keyframes.push({
      transform: poseTransform({ x: from.x + rand(rng, -3, 5), y: from.y + rand(rng, 2, 8) }),
      easing: 'cubic-bezier(0.2, 0.8, 0.25, 1)'
    })
  }

  for (let index = 0; index < hops; index += 1) {
    const last = index === hops - 1
    progress += slices[index]
    const targetX = last ? to.x : clampToward(from.x, to.x, lerp(from.x, to.x, progress) + zigzag * rand(rng, 4, 14))
    let targetY = last ? to.y : clampToward(from.y, to.y, lerp(from.y, to.y, progress) + rand(rng, 2, 14))
    if (style === 'nosey' && index === 0) targetY += rand(rng, 8, 16)
    if (style === 'zigzag') zigzag *= -1
    else if (rng() < 0.4) zigzag *= -1

    const startX = x
    const startY = y
    if (!last && dirX > 0) {
      x = Math.min(to.x - 8, Math.max(x + 4, targetX))
    } else if (!last && dirX < 0) {
      x = Math.max(to.x + 8, Math.min(x - 4, targetX))
    } else {
      x = targetX
    }
    y = last ? to.y : targetY

    const height = hopHeight(style, rng, index, hops)
    const liftX = lerp(startX, x, 0.5) + rand(rng, -3, 3)
    const liftY = Math.min(startY, y) - height

    keyframes.push({
      transform: poseTransform({ x: liftX, y: liftY }),
      easing: 'cubic-bezier(0.35, 0, 0.7, 0.2)'
    })
    keyframes.push({
      transform: poseTransform({ x, y }),
      easing: 'cubic-bezier(0.2, 0.8, 0.25, 1)'
    })
  }

  keyframes.push({ transform: poseTransform(to) })

  const duration = Math.round((style === 'dash' ? 620 : style === 'pounce' ? 880 : style === 'scamper' ? 1080 : 1240) + hops * rand(rng, 40, 90))
  return { style, hops, duration, keyframes }
}

export function readElementPose(element) {
  if (!element) return { ...CHAIR_POSE }
  const matrix = new DOMMatrix(getComputedStyle(element).transform)
  return { x: matrix.e, y: matrix.f }
}
