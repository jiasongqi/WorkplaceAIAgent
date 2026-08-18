<template>
  <Transition name="pet-bubble">
    <div v-if="message" class="pet-bubble" role="status" aria-live="polite">
      {{ message }}
    </div>
  </Transition>
</template>

<script setup>
defineProps({
  message: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.pet-bubble {
  position: absolute;
  right: 14px;
  bottom: calc(100% + 8px);
  width: max-content;
  max-width: 220px;
  padding: 9px 12px;
  border: 1px solid var(--gold-border-soft);
  border-radius: 14px 14px 4px;
  background: var(--layer1);
  box-shadow: var(--pet-widget-shadow);
  color: var(--t2);
  font-size: 12px;
  line-height: 1.5;
  pointer-events: none;
}

.pet-bubble-enter-active,
.pet-bubble-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.pet-bubble-enter-from,
.pet-bubble-leave-to {
  opacity: 0;
  transform: translateY(5px) scale(0.96);
}

@media (prefers-reduced-motion: reduce) {
  .pet-bubble-enter-active,
  .pet-bubble-leave-active {
    transition: none;
  }
}
</style>
