<template>
  <div
    class="pet-room"
    :class="[
      `chair-${chair}`,
      `rug-${rug}`,
      `presence-${presence}`,
      travel ? `travel-${travel}` : '',
      { compact }
    ]"
    :data-presence="presence"
    :data-chair="chair"
    :data-rug="rug"
  >
    <svg class="room-scene" viewBox="0 0 200 168" role="presentation">
      <rect class="room-back" x="18" y="14" width="164" height="92" rx="10" />
      <path class="room-side" d="M182 22v92L200 132V38z" />
      <path class="room-floor" d="M8 106h174l18 26H18z" />

      <g class="room-window">
        <rect x="32" y="28" width="46" height="36" rx="6" />
        <path d="M55 28v36M32 46h46" />
        <rect class="window-light" x="34" y="30" width="19" height="15" rx="3" />
      </g>

      <g class="room-plant">
        <path d="M156 86c8-16 20-16 22-2-8 2-12 8-12 14h-8c0-6-2-10-2-12z" />
        <rect x="160" y="96" width="14" height="10" rx="2" />
      </g>

      <g class="room-shelf">
        <rect x="118" y="34" width="46" height="5" rx="2" />
        <path d="M122 39v6M158 39v6" />
      </g>

      <g class="room-door">
        <path d="M176 48h18v70h-18z" />
        <circle cx="190" cy="84" r="2" />
      </g>

      <ellipse class="room-rug" cx="96" cy="142" rx="52" ry="10" />

      <g class="room-chair">
        <path class="chair-back" d="M70 58h42c6 0 10 6 10 12v28H60V70c0-6 4-12 10-12z" />
        <rect class="chair-seat" x="56" y="92" width="70" height="16" rx="6" />
        <path class="chair-leg" d="M64 108v22M118 108v22" />
      </g>
    </svg>

    <div ref="seat" class="pet-seat">
      <slot />
    </div>

    <p v-if="!compact" class="room-status">{{ statusLabel }}</p>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

import { CHAIR_POSE, DOOR_POSE, planHopTrip, readElementPose } from '../../companion/hopPath'

const props = defineProps({
  presence: {
    type: String,
    default: 'onChair'
  },
  chair: {
    type: String,
    default: 'wood'
  },
  rug: {
    type: String,
    default: 'plain'
  },
  statusLabel: {
    type: String,
    default: '陪你坐着'
  },
  compact: {
    type: Boolean,
    default: false
  },
  travel: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['travel-end'])
const seat = ref(null)
let player = null

function stopHop() {
  if (!player) return
  try {
    player.commitStyles()
  } catch {
    // Ignore if the animation already finished.
  }
  player.cancel()
  player = null
  if (seat.value) seat.value.style.transform = ''
}

async function playHop(direction) {
  if (!seat.value || props.compact) {
    emit('travel-end')
    return
  }
  stopHop()
  const live = readElementPose(seat.value)
  const start = Number.isFinite(live.x) && Math.abs(live.x) + Math.abs(live.y) > 1
    ? live
    : direction === 'out' ? { ...CHAIR_POSE } : { ...DOOR_POSE }
  const end = direction === 'out' ? { ...DOOR_POSE } : { ...CHAIR_POSE }
  const trip = planHopTrip(start, end)
  player = seat.value.animate(trip.keyframes, {
    duration: trip.duration,
    fill: 'forwards',
    easing: 'linear'
  })
  try {
    await player.finished
    emit('travel-end')
  } catch {
    // Replaced by a newer hop.
  }
}

watch(() => props.travel, direction => {
  if (!direction) {
    stopHop()
    return
  }
  playHop(direction)
})

onBeforeUnmount(() => {
  player?.cancel()
  player = null
})
</script>

<style scoped>
.pet-room {
  position: relative;
  width: 200px;
  height: 176px;
  overflow: hidden;
  border: 1px solid var(--pet-room-border);
  border-radius: 22px;
  background: var(--pet-room-bg);
  box-shadow: var(--shadow-card);
}

.room-scene {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.room-back {
  fill: var(--pet-room-wall);
}

.room-side {
  fill: var(--pet-room-wall-side);
}

.room-floor {
  fill: var(--pet-room-floor);
}

.room-window rect,
.room-window path {
  fill: var(--pet-room-window);
  stroke: var(--pet-room-window-stroke);
  stroke-width: 2;
}

.window-light {
  fill: var(--pet-room-window-light);
  stroke: none;
}

.room-plant path {
  fill: var(--pet-room-plant);
}

.room-plant rect {
  fill: var(--pet-room-plant-pot);
}

.room-shelf rect,
.room-shelf path {
  fill: none;
  stroke: var(--pet-room-shelf);
  stroke-width: 2.4;
  stroke-linecap: round;
}

.room-door path {
  fill: var(--pet-room-door);
  stroke: var(--pet-room-door-stroke);
  stroke-width: 2;
}

.room-door circle {
  fill: var(--pet-room-knob);
}

.room-rug {
  fill: var(--pet-room-rug);
}

.chair-wood .chair-back,
.chair-wood .chair-seat {
  fill: var(--pet-chair-wood);
  stroke: var(--pet-chair-wood-stroke);
  stroke-width: 2;
}

.chair-velvet .chair-back,
.chair-velvet .chair-seat {
  fill: var(--pet-chair-velvet);
  stroke: var(--pet-chair-velvet-stroke);
  stroke-width: 2;
}

.chair-leg {
  fill: none;
  stroke: var(--pet-chair-leg);
  stroke-width: 4;
  stroke-linecap: round;
}

.pet-seat {
  position: absolute;
  left: 52px;
  top: 42px;
  width: 96px;
  height: 88px;
  overflow: visible;
  background: transparent;
  transform-origin: 50% 92%;
  backface-visibility: hidden;
}

.pet-seat :deep(.pet-stage) {
  width: 96px;
  height: 88px;
}

.presence-away .pet-seat {
  transform: translate3d(78px, 12px, 0);
}

.travel-out :deep(.cat-sprite),
.travel-in :deep(.cat-sprite) {
  animation: none;
}

.room-status {
  position: absolute;
  right: 10px;
  bottom: 8px;
  margin: 0;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--pet-status-bg);
  color: var(--pet-status-text);
  font-size: 11px;
  line-height: 1.3;
}

.compact {
  width: 100%;
  height: 84px;
  border-radius: 12px;
}

.compact .room-status,
.compact .room-shelf,
.compact .room-plant {
  display: none;
}

.compact .pet-seat {
  left: 26%;
  top: 2px;
  width: 72px;
  height: 72px;
}

.compact .pet-seat :deep(.pet-stage) {
  width: 72px;
  height: 72px;
}
</style>
