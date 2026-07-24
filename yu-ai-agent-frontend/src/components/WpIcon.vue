<template>
  <span
    class="wp-icon"
    :class="[`size-${size}`, { active }]"
    :style="inlineSize"
    role="img"
    :aria-label="label || name"
  >
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <template v-for="(el, i) in elements" :key="i">
        <path v-if="el.t === 'p'" :d="el.d" />
        <circle v-else-if="el.t === 'c'" :cx="el.cx" :cy="el.cy" :r="el.r" />
        <rect v-else-if="el.t === 'r'" :x="el.x" :y="el.y" :width="el.w" :height="el.h" :rx="el.rx || 0" />
      </template>
    </svg>
  </span>
</template>

<script setup>
import { computed } from 'vue'

/** WorkPilot Icon Set v1 — 24×24 stroke, currentColor */
const ICONS = {
  home: [
    { t: 'p', d: 'M4.5 10.5 12 4l7.5 6.5V20a1 1 0 0 1-1 1h-4.5v-5.5h-5V21H5.5a1 1 0 0 1-1-1v-9.5Z' },
  ],
  career: [
    { t: 'p', d: 'M5 7.5A2.5 2.5 0 0 1 7.5 5h9A2.5 2.5 0 0 1 19 7.5v6A2.5 2.5 0 0 1 16.5 16H11l-3.5 3v-3H7.5A2.5 2.5 0 0 1 5 13.5v-6Z' },
    { t: 'p', d: 'M9 9.5h6M9 12.5h3.5' },
  ],
  agent: [
    { t: 'c', cx: 12, cy: 12, r: 3.2 },
    { t: 'p', d: 'M12 3.5v2.2M12 18.3v2.2M3.5 12h2.2M18.3 12h2.2M6.1 6.1l1.55 1.55M16.35 16.35l1.55 1.55M17.9 6.1l-1.55 1.55M7.65 16.35l-1.55 1.55' },
  ],
  knowledge: [
    { t: 'p', d: 'M4.5 6.2c2.2-1.1 4.6-1.4 7.5.2 2.9-1.6 5.3-1.3 7.5-.2V18c-2.2-1.1-4.6-1.4-7.5.2-2.9-1.6-5.3-1.3-7.5-.2V6.2Z' },
    { t: 'p', d: 'M12 6.5v11.7' },
  ],
  artifact: [
    { t: 'p', d: 'M12 3.5 20 8v8l-8 4.5L4 16V8l8-4.5Z' },
    { t: 'p', d: 'M12 12.2 20 8M12 12.2 4 8M12 12.2V20.5' },
  ],
  star: [
    { t: 'p', d: 'm12 3.8 2.2 4.7 5.1.6-3.8 3.5 1.1 5-4.6-2.6-4.6 2.6 1.1-5-3.8-3.5 5.1-.6L12 3.8Z' },
  ],
  usage: [
    { t: 'p', d: 'M4.5 19.5h15' },
    { t: 'p', d: 'M7.5 16.5v-4M12 16.5V8.5M16.5 16.5v-7' },
  ],
  admin: [
    { t: 'c', cx: 12, cy: 12, r: 2.8 },
    { t: 'p', d: 'M12 3.8l1.1 1.9 2.1-.3.9 1.9 1.9.9-.3 2.1 1.9 1.1-1.9 1.1.3 2.1-1.9.9-.9 1.9-2.1-.3L12 20.2l-1.1-1.9-2.1.3-.9-1.9-1.9-.9.3-2.1L3.5 12l1.9-1.1-.3-2.1 1.9-.9.9-1.9 2.1.3L12 3.8Z' },
  ],
  search: [
    { t: 'c', cx: 11, cy: 11, r: 6 },
    { t: 'p', d: 'm16.2 16.2 3.3 3.3' },
  ],
  lock: [
    { t: 'r', x: 5.5, y: 10.5, w: 13, h: 9.5, rx: 2 },
    { t: 'p', d: 'M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5' },
  ],
  salary: [
    { t: 'c', cx: 12, cy: 12, r: 8 },
    { t: 'p', d: 'M12 7.5v9M9.5 9.8c.7-1 1.8-1.5 2.8-1.5 1.7 0 2.7.9 2.7 2.1s-1 2.1-2.7 2.1h-1.1c-1.7 0-2.7.9-2.7 2.1s1 2.1 2.7 2.1c1.1 0 2.1-.5 2.8-1.5' },
  ],
  path: [
    { t: 'c', cx: 6.5, cy: 17.5, r: 2 },
    { t: 'c', cx: 17.5, cy: 6.5, r: 2 },
    { t: 'p', d: 'M8.3 16.2c2.2-4.8 5.2-7.2 9-9' },
  ],
  compass: [
    { t: 'c', cx: 12, cy: 12, r: 8.5 },
    { t: 'p', d: 'm14.8 9.2-1.3 4.3-4.3 1.3 1.3-4.3 4.3-1.3Z' },
  ],
  interview: [
    { t: 'r', x: 6, y: 5, w: 12, h: 15, rx: 2 },
    { t: 'p', d: 'M9 5.2V4.5a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 4.5v.7M9 11h6M9 14.5h4' },
  ],
  compare: [
    { t: 'r', x: 3.5, y: 5, w: 7, h: 14, rx: 1.5 },
    { t: 'r', x: 13.5, y: 5, w: 7, h: 14, rx: 1.5 },
    { t: 'p', d: 'M6 9.5h2M6 12.5h2M16 9.5h2M16 12.5h2' },
  ],
  resume: [
    { t: 'p', d: 'M7 4.5h7.5L18.5 9v10.5a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-14a1 1 0 0 1 1-1Z' },
    { t: 'p', d: 'M14.5 4.5V9H18.5M9 13h6M9 16.5h4' },
  ],
  calendar: [
    { t: 'r', x: 4, y: 5.5, w: 16, h: 14, rx: 2 },
    { t: 'p', d: 'M8 3.5v4M16 3.5v4M4 10h16' },
  ],
  target: [
    { t: 'c', cx: 12, cy: 12, r: 8 },
    { t: 'c', cx: 12, cy: 12, r: 4.5 },
    { t: 'c', cx: 12, cy: 12, r: 1.2 },
  ],
  manage: [
    { t: 'c', cx: 8, cy: 8, r: 2.5 },
    { t: 'c', cx: 16, cy: 8, r: 2.5 },
    { t: 'p', d: 'M4.5 18.5c.4-2.8 2.2-4.5 4.5-4.5.9 0 1.7.3 2.4.8.7-.5 1.5-.8 2.4-.8 2.3 0 4.1 1.7 4.5 4.5' },
  ],
  archive: [
    { t: 'r', x: 3.5, y: 4, w: 17, h: 4, rx: 1 },
    { t: 'p', d: 'M5 8v10.5a1.5 1.5 0 0 0 1.5 1.5h11a1.5 1.5 0 0 0 1.5-1.5V8M10 12.5h4' },
  ],
  user: [
    { t: 'c', cx: 12, cy: 9, r: 3.2 },
    { t: 'p', d: 'M5.5 19c1.2-3 3.3-4.5 6.5-4.5s5.3 1.5 6.5 4.5' },
  ],
  send: [
    { t: 'p', d: 'M5 12h12.5M13 6.5 18.5 12 13 17.5' },
  ],
  menu: [
    { t: 'p', d: 'M4.5 7h15M4.5 12h15M4.5 17h15' },
  ],
  edit: [
    { t: 'p', d: 'M14.2 5.3l4.5 4.5M5 19l.9-4.2L15.5 5.2a1.5 1.5 0 0 1 2.1 0l1.2 1.2a1.5 1.5 0 0 1 0 2.1L9.2 18.1 5 19Z' },
  ],
}

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 18 },
  active: { type: Boolean, default: false },
  label: { type: String, default: '' },
})

const elements = computed(() => ICONS[props.name] || ICONS.home)
const sizePx = computed(() => `${Number(props.size)}px`)
const inlineSize = computed(() => ({ width: sizePx.value, height: sizePx.value }))
</script>

<style scoped>
.wp-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: inherit;
  line-height: 0;
  vertical-align: middle;
}
.wp-icon svg {
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.wp-icon.active {
  color: var(--gold-text);
}
</style>
