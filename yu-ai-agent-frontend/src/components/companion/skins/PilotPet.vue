<template>
  <div
    class="pet-stage"
    :class="[`state-${state}`, `motion-${motion}`, `presence-${presence}`]"
    :data-state="state"
    :data-presence="presence"
    aria-hidden="true"
  >
    <svg viewBox="0 0 120 120" role="presentation">
      <ellipse class="pet-shadow" cx="60" cy="108" rx="28" ry="6" />

      <g class="pet-character">
        <path class="pet-antenna" d="M60 23v-9" />
        <circle class="pet-antenna-tip" cx="60" cy="11" r="4" />

        <g class="pet-head">
          <path class="pet-ear pet-ear-left" d="M31 43 20 35v23l12-5z" />
          <path class="pet-ear pet-ear-right" d="m89 43 11-8v23l-12-5z" />
          <rect class="pet-face" x="29" y="28" width="62" height="48" rx="23" />
          <circle class="pet-eye pet-eye-left" cx="49" cy="50" r="4" />
          <circle class="pet-eye pet-eye-right" cx="71" cy="50" r="4" />
          <path
            class="pet-mouth"
            :d="state === 'error' ? 'M54 66q6-5 12 0' : 'M54 62q6 5 12 0'"
          />
          <circle class="pet-cheek pet-cheek-left" cx="40" cy="61" r="4" />
          <circle class="pet-cheek pet-cheek-right" cx="80" cy="61" r="4" />
        </g>

        <g class="pet-body">
          <path class="pet-torso" d="M40 76q20-9 40 0l7 28H33z" />
          <path class="pet-badge" d="m60 79 6 5-2 8h-8l-2-8z" />
          <path class="pet-arm pet-arm-left" d="M39 80 26 93" />
          <path class="pet-arm pet-arm-right" d="m81 80 13 13" />
        </g>

        <g class="pet-document">
          <rect x="70" y="78" width="22" height="27" rx="3" />
          <path d="M75 85h12M75 91h9M75 97h11" />
        </g>
      </g>

      <g class="pet-thinking-mark">
        <circle cx="91" cy="25" r="3" />
        <circle cx="101" cy="20" r="3" />
        <circle cx="111" cy="16" r="3" />
      </g>

      <g class="pet-question">
        <path d="M96 30c0-8 13-8 13 0 0 6-7 5-7 11" />
        <circle cx="102" cy="49" r="2" />
      </g>

      <g class="pet-alert">
        <path d="m101 14 11 20H90z" />
        <path d="M101 21v6" />
        <circle cx="101" cy="31" r="1" />
      </g>

      <g class="pet-stars">
        <path d="m17 37 2 5 5 2-5 2-2 5-2-5-5-2 5-2z" />
        <path d="m100 61 2 4 4 2-4 2-2 4-2-4-4-2 4-2z" />
        <path d="m27 20 1.5 3.5L32 25l-3.5 1.5L27 30l-1.5-3.5L22 25l3.5-1.5z" />
      </g>

      <g class="pet-route-badge">
        <circle cx="99" cy="31" r="12" />
        <path d="M92 31h14m-5-5 5 5-5 5" />
      </g>
    </svg>
  </div>
</template>

<script setup>
defineProps({
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
</script>

<style scoped>
.pet-stage {
  width: 104px;
  height: 104px;
  color: var(--gold-text);
}

.pet-stage svg {
  width: 100%;
  height: 100%;
  overflow: visible;
}

.pet-shadow {
  fill: var(--pet-ground-shadow);
}

.pet-character {
  transform-origin: 60px 104px;
  animation: pet-breathe 3.8s ease-in-out infinite;
}

.pet-face {
  fill: var(--layer1);
  stroke: var(--gold);
  stroke-width: 3;
}

.pet-ear,
.pet-torso {
  fill: var(--gold-soft);
  stroke: var(--gold);
  stroke-width: 3;
  stroke-linejoin: round;
}

.pet-antenna,
.pet-mouth,
.pet-arm,
.pet-document path,
.pet-route-badge path,
.pet-question,
.pet-alert path {
  fill: none;
  stroke: currentColor;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.pet-antenna-tip,
.pet-eye,
.pet-badge,
.pet-route-badge circle {
  fill: var(--gold);
}

.pet-cheek {
  fill: rgba(244, 114, 182, 0.28);
}

.pet-document {
  opacity: 0;
}

.pet-document rect {
  fill: var(--layer1);
  stroke: var(--gold);
  stroke-width: 2;
}

.pet-thinking-mark,
.pet-question,
.pet-alert,
.pet-stars,
.pet-route-badge {
  opacity: 0;
}

.pet-thinking-mark circle,
.pet-stars path {
  fill: var(--gold);
}

.pet-alert path {
  fill: var(--warning-bg, #fff7ed);
  stroke: var(--warning, #f59e0b);
}

.state-listening .pet-ear-left {
  transform-origin: 31px 48px;
  transform: rotate(-12deg);
}

.state-listening .pet-ear-right {
  transform-origin: 89px 48px;
  transform: rotate(12deg);
}

.state-thinking .pet-thinking-mark {
  opacity: 1;
}

.state-thinking .pet-head {
  transform-origin: 60px 70px;
  animation: pet-think 1.8s ease-in-out infinite;
}

.state-routed .pet-route-badge {
  opacity: 1;
  animation: pet-pop 0.55s ease both;
}

.state-working .pet-document {
  opacity: 1;
}

.state-working .pet-arm-right {
  transform-origin: 81px 80px;
  animation: pet-write 0.55s ease-in-out infinite alternate;
}

.state-confused .pet-question {
  opacity: 1;
  animation: pet-pop 0.4s ease both;
}

.state-confused .pet-head {
  transform-origin: 60px 70px;
  transform: rotate(8deg);
}

.state-celebrate .pet-stars {
  opacity: 1;
  animation: pet-sparkle 0.8s ease both;
}

.state-celebrate .pet-character {
  animation: pet-jump 0.8s ease both;
}

.state-alert .pet-alert {
  opacity: 1;
  animation: pet-pop 0.4s ease both;
}

.state-alert .pet-character,
.state-error .pet-character {
  animation: pet-shake 0.45s ease 2;
}

.state-greeting .pet-arm-right {
  transform-origin: 81px 80px;
  animation: pet-wave 0.45s ease-in-out 3 alternate;
}

.motion-reduced .pet-character,
.motion-reduced .pet-head,
.motion-reduced .pet-arm-right,
.motion-off .pet-character,
.motion-off .pet-head,
.motion-off .pet-arm-right {
  animation: none;
}

@keyframes pet-breathe {
  0%, 100% { transform: translateY(0) scaleY(1); }
  50% { transform: translateY(-2px) scaleY(1.015); }
}

@keyframes pet-think {
  0%, 100% { transform: rotate(0); }
  50% { transform: rotate(-5deg); }
}

@keyframes pet-write {
  from { transform: rotate(-4deg); }
  to { transform: rotate(8deg); }
}

@keyframes pet-wave {
  from { transform: rotate(0); }
  to { transform: rotate(-25deg); }
}

@keyframes pet-jump {
  0%, 100% { transform: translateY(0); }
  45% { transform: translateY(-15px) rotate(-3deg); }
}

@keyframes pet-shake {
  0%, 100% { transform: translateX(0); }
  30% { transform: translateX(-4px); }
  70% { transform: translateX(4px); }
}

@keyframes pet-pop {
  from { opacity: 0; transform: scale(0.5); }
  to { opacity: 1; transform: scale(1); }
}

@keyframes pet-sparkle {
  from { opacity: 0; transform: scale(0.5); }
  60% { opacity: 1; }
  to { opacity: 0; transform: scale(1.25); }
}

@media (prefers-reduced-motion: reduce) {
  .pet-character,
  .pet-head,
  .pet-arm-right,
  .pet-thinking-mark,
  .pet-route-badge,
  .pet-question,
  .pet-alert,
  .pet-stars {
    animation: none !important;
  }
}
</style>
