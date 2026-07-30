import { ref, watch } from 'vue'

const STORAGE_KEY = 'wp-theme'
const DEFAULT_THEME = 'sage'

const theme = ref(
  (typeof localStorage !== 'undefined' && localStorage.getItem(STORAGE_KEY)) || DEFAULT_THEME
)

function applyTheme(value) {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', value)
}

applyTheme(theme.value)

watch(theme, (value) => {
  applyTheme(value)
  localStorage.setItem(STORAGE_KEY, value)
})

export function useTheme() {
  const toggleTheme = () => {
    theme.value = theme.value === 'sage' ? 'dark' : 'sage'
  }

  const setTheme = (value) => {
    if (value === 'sage' || value === 'dark') theme.value = value
  }

  return { theme, toggleTheme, setTheme }
}
