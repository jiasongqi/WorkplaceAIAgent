<template>
  <div
    class="pet-stage cat-pet"
    :class="[`state-${state}`, `motion-${motion}`, `presence-${presence}`, `pose-${pose}`, `facing-${facing}`]"
    :data-state="state"
    :data-presence="presence"
    :data-pose="pose"
    :data-facing="facing"
    aria-hidden="true"
  >
    <img class="cat-sprite" :src="spriteSrc" :alt="''" draggable="false">
  </div>
</template>

<script setup>
import { computed } from 'vue'

import { resolveCatPose } from '../../../companion/catalog'
import celebrateSrc from '../../../assets/companion/cat/v5_celebrate_birthday.png'
import greetingSrc from '../../../assets/companion/cat/v5_greeting_wave.png'
import idleSrc from '../../../assets/companion/cat/v5_idle_sit.png'
import snackSrc from '../../../assets/companion/cat/v5_snack_burger.png'
import thinkingSrc from '../../../assets/companion/cat/v5_thinking.png'
import walkSrc from '../../../assets/companion/cat/v5_walk_away.png'

const SPRITES = {
  idle: idleSrc,
  greeting: greetingSrc,
  thinking: thinkingSrc,
  walk: walkSrc,
  celebrate: celebrateSrc,
  snack: snackSrc
}

const props = defineProps({
  state: {
    type: String,
    default: 'idle'
  },
  motion: {
    type: String,
    default: 'full'
  },
  presence: {
    type: String,
    default: 'onChair'
  },
  travel: {
    type: String,
    default: ''
  }
})

const pose = computed(() => resolveCatPose(props.state, props.presence))
const facing = computed(() => (props.travel === 'in' ? 'left' : 'right'))
const spriteSrc = computed(() => SPRITES[pose.value] || SPRITES.idle)
</script>

<style scoped>
.pet-stage {
  width: 104px;
  height: 104px;
  background: transparent;
  filter: drop-shadow(var(--pet-sprite-shadow));
}

.cat-sprite {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center bottom;
  border: 0;
  background: transparent;
  pointer-events: none;
  transform-origin: 50% 92%;
  backface-visibility: hidden;
  animation: pet-breathe 3.4s ease-in-out infinite;
}

.pose-walk.facing-right .cat-sprite {
  transform: scaleX(-1);
  animation: none;
}

.pose-walk.facing-left .cat-sprite {
  transform: none;
  animation: none;
}

.pose-celebrate .cat-sprite {
  animation: pet-jump 0.8s ease both;
}

.pose-greeting .cat-sprite {
  animation: pet-breathe 1.2s ease-in-out infinite;
}

.state-alert .cat-sprite,
.state-error .cat-sprite {
  animation: pet-shake 0.45s ease 2;
}

.motion-reduced .cat-sprite,
.motion-off .cat-sprite {
  animation: none;
}

@keyframes pet-breathe {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}

@keyframes pet-jump {
  0%, 100% { transform: translateY(0); }
  45% { transform: translateY(-11px); }
}

@keyframes pet-shake {
  0%, 100% { transform: translateX(0); }
  30% { transform: translateX(-3px); }
  70% { transform: translateX(3px); }
}

@media (prefers-reduced-motion: reduce) {
  .cat-sprite {
    animation: none !important;
  }
}
</style>
