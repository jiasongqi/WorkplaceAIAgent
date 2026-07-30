/**
 * Headless Edge screenshots via CDP (no extra deps).
 * Usage: node scripts/capture-screenshots.mjs
 * Optional: BASE=http://[::1]:3000  EDGE_PATH=...
 */
import { spawn } from 'node:child_process'
import { setTimeout as sleep } from 'node:timers/promises'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '../..')
const OUT = path.join(ROOT, 'docs/assets')
const EDGE = process.env.EDGE_PATH ||
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const PORT = 9466
const userData = path.join(ROOT, 'tmp-edge-cdp-2')
const BASE = process.env.BASE || 'http://[::1]:3000'

const PAGES = [
  { name: 'screenshot-login.png', url: `${BASE}/login`, waitMs: 2800 },
  { name: 'screenshot-home.png', url: `${BASE}/`, waitMs: 4000, guest: true },
  { name: 'screenshot-career.png', url: `${BASE}/chat/career`, waitMs: 4000, guest: true },
  { name: 'screenshot-super.png', url: `${BASE}/chat/super`, waitMs: 3500, guest: true },
  { name: 'screenshot-knowledge.png', url: `${BASE}/knowledge`, waitMs: 3500, guest: true },
  {
    name: 'screenshot-companion.png',
    url: `${BASE}/chat/career`,
    waitMs: 4500,
    guest: true,
    click: '.top-pill.companion',
    waitSel: '.overlay-badge.companion',
    waitAfterClickMs: 1200,
  },
  {
    name: 'screenshot-digital-employee.png',
    url: `${BASE}/chat/career`,
    waitMs: 4500,
    guest: true,
    click: '.top-pill.employee',
    waitSel: '.overlay-badge.employee',
    waitAfterClickMs: 1500,
  },
]

async function cdp(ws, method, params = {}, sessionId) {
  const id = cdp._id = (cdp._id || 0) + 1
  const msg = { id, method, params }
  if (sessionId) msg.sessionId = sessionId
  ws.send(JSON.stringify(msg))
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`CDP timeout: ${method}`)), 30000)
    const onMsg = (event) => {
      const data = JSON.parse(typeof event.data === 'string' ? event.data : event.data.toString())
      if (data.id !== id) return
      clearTimeout(t)
      ws.removeEventListener('message', onMsg)
      if (data.error) reject(new Error(JSON.stringify(data.error)))
      else resolve(data.result)
    }
    ws.addEventListener('message', onMsg)
  })
}

async function fetchJson(url) {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`HTTP ${res.status} ${url}`)
  return res.json()
}

async function guestLogin() {
  const url = 'http://127.0.0.1:8123/api/session/login?username=' +
    encodeURIComponent('游客') + '&password=' + encodeURIComponent('workpilot-local')
  try {
    const res = await fetch(url, { method: 'POST' })
    const body = await res.json()
    const data = body?.data || body
    return {
      token: data?.token || data?.accessToken || '',
      username: data?.username || '游客',
      role: data?.role || 'GUEST',
    }
  } catch (e) {
    console.warn('guest login skipped:', e.message)
    return null
  }
}

async function waitForPage(ws, sessionId) {
  for (let i = 0; i < 20; i++) {
    const { result } = await cdp(ws, 'Runtime.evaluate', {
      expression: `(() => {
        const t = document.querySelector('#app')?.innerText?.trim() || '';
        const hasTop = !!document.querySelector('.topbar, .logo-text, .login-page, .home, .chat-layout, .chat-core');
        return { len: t.length, hasTop, href: location.href };
      })()`,
      returnByValue: true,
    }, sessionId)
    const v = result?.value || {}
    if (v.len > 40 && v.hasTop) return v
    await sleep(500)
  }
  return null
}

async function main() {
  await fs.mkdir(userData, { recursive: true })
  await fs.mkdir(OUT, { recursive: true })

  const edge = spawn(EDGE, [
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${userData}`,
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--hide-scrollbars',
    'about:blank',
  ], { stdio: 'ignore' })

  let ready = false
  for (let i = 0; i < 40; i++) {
    try {
      await fetchJson(`http://127.0.0.1:${PORT}/json/version`)
      ready = true
      break
    } catch {
      await sleep(250)
    }
  }
  if (!ready) throw new Error('Edge CDP not ready')

  const version = await fetchJson(`http://127.0.0.1:${PORT}/json/version`)
  const ws = new WebSocket(version.webSocketDebuggerUrl)

  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true })
    ws.addEventListener('error', reject, { once: true })
  })

  const auth = await guestLogin()
  console.log('BASE=', BASE, 'auth=', auth?.username || 'none')

  for (const page of PAGES) {
    const { targetId } = await cdp(ws, 'Target.createTarget', { url: 'about:blank' })
    const { sessionId } = await cdp(ws, 'Target.attachToTarget', { targetId, flatten: true })
    await cdp(ws, 'Emulation.setDeviceMetricsOverride', {
      width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
    }, sessionId)
    await cdp(ws, 'Page.enable', {}, sessionId)

    if (auth?.token && page.guest) {
      await cdp(ws, 'Page.addScriptToEvaluateOnNewDocument', {
        source: `localStorage.setItem('token', ${JSON.stringify(auth.token)});
localStorage.setItem('username', ${JSON.stringify(auth.username)});
localStorage.setItem('role', ${JSON.stringify(auth.role)});`,
      }, sessionId)
    } else if (!page.guest) {
      await cdp(ws, 'Page.addScriptToEvaluateOnNewDocument', {
        source: `localStorage.removeItem('token'); localStorage.removeItem('username'); localStorage.removeItem('role');`,
      }, sessionId)
    }

    const nav = await cdp(ws, 'Page.navigate', { url: page.url }, sessionId)
    if (nav?.errorText) console.warn('nav warn', page.name, nav.errorText)
    await sleep(page.waitMs)
    await waitForPage(ws, sessionId)
    await sleep(800)

    if (page.click) {
      const { result } = await cdp(ws, 'Runtime.evaluate', {
        expression: `(() => {
          const el = document.querySelector(${JSON.stringify(page.click)});
          if (!el) return { ok: false, reason: 'missing' };
          el.click();
          return { ok: true, text: (el.innerText || '').trim() };
        })()`,
        returnByValue: true,
      }, sessionId)
      console.log('click', page.name, result?.value)
      await sleep(page.waitAfterClickMs || 1200)
    }

    const shot = await cdp(ws, 'Page.captureScreenshot', {
      format: 'png',
      fromSurface: true,
      captureBeyondViewport: false,
    }, sessionId)

    const outPath = path.join(OUT, page.name)
    await fs.writeFile(outPath, Buffer.from(shot.data, 'base64'))
    const st = await fs.stat(outPath)
    console.log(`OK ${page.name} (${st.size} bytes) ← ${page.url}`)

    await cdp(ws, 'Target.closeTarget', { targetId })
  }

  ws.close()
  edge.kill()
  console.log('done')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
