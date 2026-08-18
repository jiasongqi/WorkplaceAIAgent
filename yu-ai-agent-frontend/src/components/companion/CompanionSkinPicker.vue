<template>
  <div class="skin-picker" role="group" aria-label="伙伴外形">
    <button
      v-for="skin in PET_SKINS"
      :key="skin.id"
      class="skin-card"
      type="button"
      :aria-pressed="skin.selectable && modelValue === skin.id"
      :aria-disabled="!skin.selectable"
      :disabled="!skin.selectable"
      :class="{ selected: modelValue === skin.id, locked: !skin.selectable }"
      @click="select(skin)"
    >
      <div class="skin-preview">
        <PetRoom
          v-if="skin.selectable"
          compact
          presence="onChair"
          status-label=""
        >
          <PetStage
            :skin="skin.id"
            state="idle"
            motion="reduced"
            presence="onChair"
          />
        </PetRoom>
        <span v-else class="skin-soon" aria-hidden="true">🐼</span>
      </div>
      <strong>{{ skin.name }}</strong>
      <span>{{ skin.selectable ? skin.blurb : '即将开放' }}</span>
    </button>
  </div>
</template>

<script setup>
import { PET_SKINS } from '../../companion/catalog'
import PetRoom from './PetRoom.vue'
import PetStage from './PetStage.vue'

defineProps({
  modelValue: {
    type: String,
    default: 'cat'
  }
})

const emit = defineEmits(['update:modelValue'])

function select(skin) {
  if (!skin.selectable) return
  emit('update:modelValue', skin.id)
}
</script>

<style scoped>
.skin-picker {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.skin-card {
  display: grid;
  gap: 4px;
  min-height: 132px;
  padding: 8px 8px 10px;
  border: 1px solid var(--glass-border);
  border-radius: 14px;
  background: var(--layer2);
  color: var(--t3);
  text-align: left;
  cursor: pointer;
}

.skin-card strong {
  color: var(--t1);
  font-size: 13px;
}

.skin-card span {
  font-size: 11px;
  line-height: 1.4;
}

.skin-card.selected {
  border-color: var(--gold-border);
  box-shadow: 0 0 0 3px var(--gold-dim);
  background: var(--gold-soft);
}

.skin-card.locked {
  opacity: 0.62;
  cursor: not-allowed;
}

.skin-card:focus-visible {
  outline: 3px solid var(--gold);
  outline-offset: 2px;
}

.skin-preview {
  display: grid;
  place-items: center;
  height: 84px;
  overflow: hidden;
}

.skin-soon {
  font-size: 28px;
}

@media (max-width: 640px) {
  .skin-picker {
    grid-template-columns: 1fr;
  }
}
</style>
