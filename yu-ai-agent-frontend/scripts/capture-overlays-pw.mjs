/**
 * Playwright overlay screenshots (companion + digital employee).
 * Usage: node scripts/capture-overlays-pw.mjs
 */
import { chromium } from 'playwright'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '../..')
const OUT = path.join(ROOT, 'docs/assets')
const BASE = process.env.BASE || 'http://[::1]:3000'

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

async function openCareer(page, auth) {
  await page.addInitScript((a) => {
    localStorage.setItem('token', a.token)
    if (a.refreshToken) localStorage.setItem('refreshToken', a.refreshToken)
    localStorage.setItem('username', a.username)
    localStorage.setItem('role', a.role)
  }, auth)
  await page.goto(`${BASE}/chat/career`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForSelector('.chat-core', { timeout: 30000 })
  await page.waitForTimeout(2000)
}

async function main() {
  await fs.mkdir(OUT, { recursive: true })
  const auth = await guestLogin()
  if (!auth.token) throw new Error('guest login failed')
  console.log('BASE=', BASE, 'user=', auth.username)

  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
  })

  // Career welcome (refresh main shot with new features visible)
  {
    const page = await context.newPage()
    await openCareer(page, auth)
    await page.screenshot({ path: path.join(OUT, 'screenshot-career.png'), fullPage: false })
    console.log('OK screenshot-career.png')
    await page.close()
  }

  // Companion overlay
  {
    const page = await context.newPage()
    await openCareer(page, auth)
    await page.click('.top-pill.companion')
    await page.waitForSelector('.overlay-badge.companion', { timeout: 10000 })
    await page.waitForTimeout(600)
    await page.screenshot({ path: path.join(OUT, 'screenshot-companion.png'), fullPage: false })
    console.log('OK screenshot-companion.png')
    await page.close()
  }

  // Digital employee overlay
  {
    const page = await context.newPage()
    await openCareer(page, auth)
    await page.click('.top-pill.employee')
    await page.waitForSelector('.overlay-badge.employee', { timeout: 10000 })
    await page.waitForTimeout(800)
    await page.screenshot({ path: path.join(OUT, 'screenshot-digital-employee.png'), fullPage: false })
    console.log('OK screenshot-digital-employee.png')
    await page.close()
  }

  await browser.close()
  console.log('done')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
