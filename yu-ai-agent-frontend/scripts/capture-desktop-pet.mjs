/**
 * Desktop pet (CompanionPet) screenshots for docs.
 * Usage: node scripts/capture-desktop-pet.mjs
 */
import { chromium } from 'playwright'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '../..')
const OUT = path.join(ROOT, 'docs/assets')
const BASE = process.env.BASE || 'http://localhost:3000'

async function ensurePetEnabled(token) {
  await fetch('http://127.0.0.1:8123/api/companion/me', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      stylePrefs: {
        pet: {
          enabled: true,
          skin: 'cat',
          motion: 'full',
          bubbleLevel: 'key',
          world: { presence: 'onChair', chair: 'wood', rug: 'plain', gifts: [] },
        },
      },
    }),
  }).catch(err => console.warn('ensurePetEnabled:', err.message))
}

async function guestLogin() {
  const url =
    'http://127.0.0.1:8123/api/session/login?username=' +
    encodeURIComponent('游客') +
    '&password=' +
    encodeURIComponent('workpilot-local')
  const res = await fetch(url, { method: 'POST' })
  const body = await res.json()
  const data = body?.data || body
  return {
    token: data?.token || data?.accessToken || '',
    refreshToken: data?.refreshToken || data?.refreshJwt || '',
    username: data?.username || '游客',
    role: data?.role || 'GUEST',
  }
}

async function openCareer(page, auth, theme = 'sage') {
  await page.addInitScript(({ a, t }) => {
    localStorage.setItem('token', a.token)
    if (a.refreshToken) localStorage.setItem('refreshToken', a.refreshToken)
    localStorage.setItem('username', a.username)
    localStorage.setItem('role', a.role)
    localStorage.removeItem('wp-companion-collapsed')
    localStorage.setItem('wp-theme', t)
    localStorage.setItem('wp-companion-layout', JSON.stringify({ x: 1180, y: 620 }))
  }, { a: auth, t: theme })
  await page.goto(`${BASE}/chat/career`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForSelector('.chat-core', { timeout: 30000 })
  await page.waitForSelector('.companion-pet', { timeout: 30000 })
  await page.waitForFunction(() => {
    const pet = document.querySelector('.companion-pet')
    return pet && !pet.classList.contains('collapsed')
  }, { timeout: 30000 })
  await page.waitForTimeout(2000)
}

async function positionPet(page) {
  await page.evaluate(() => {
    const pet = document.querySelector('.companion-pet')
    if (!pet) return
    pet.style.left = 'auto'
    pet.style.right = '24px'
    pet.style.top = 'auto'
    pet.style.bottom = '96px'
  })
  await page.waitForTimeout(400)
}

async function captureTheme(context, auth, theme) {
  const page = await context.newPage()
  await openCareer(page, auth, theme)
  await positionPet(page)

  const pet = page.locator('.companion-pet:not(.collapsed)')
  await pet.screenshot({ path: path.join(OUT, `screenshot-desktop-pet-${theme}.png`) })
  console.log(`OK screenshot-desktop-pet-${theme}.png`)

  await page.screenshot({ path: path.join(OUT, `screenshot-career-with-pet-${theme}.png`) })
  console.log(`OK screenshot-career-with-pet-${theme}.png`)
  await page.close()
}

async function captureSettings(context, auth) {
  const page = await context.newPage()
  await openCareer(page, auth, 'sage')
  await page.click('.top-pill.companion')
  await page.waitForSelector('.skin-picker', { timeout: 10000 })
  await page.waitForTimeout(600)
  const panel = page.locator('.overlay-panel')
  await panel.screenshot({ path: path.join(OUT, 'screenshot-desktop-pet-settings.png') })
  console.log('OK screenshot-desktop-pet-settings.png')
  await page.close()
}

async function main() {
  await fs.mkdir(OUT, { recursive: true })
  const auth = await guestLogin()
  if (!auth.token) throw new Error('guest login failed')
  await ensurePetEnabled(auth.token)

  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
  })

  await captureTheme(context, auth, 'sage')
  await captureTheme(context, auth, 'dark')
  await captureSettings(context, auth)

  await browser.close()
  console.log('done')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
