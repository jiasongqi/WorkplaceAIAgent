<template>
  <component
    :is="skinComponent"
    :state="state"
    :motion="motion"
    :presence="presence"
    :travel="travel"
  />
</template>

<script setup>
import { computed } from 'vue'

import { DEFAULT_SKIN_ID, resolveSkinId } from '../../companion/catalog'
import CatPet from './skins/CatPet.vue'
import PilotPet from './skins/PilotPet.vue'

const SKIN_VIEWS = {
  cat: CatPet,
  pilot: PilotPet
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
  skin: {
    type: String,
    default: 'cat'
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

const skinComponent = computed(() => {
  const id = resolveSkinId(props.skin)
  return SKIN_VIEWS[id] || SKIN_VIEWS[DEFAULT_SKIN_ID]
})
</script>
