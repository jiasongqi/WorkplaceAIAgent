/**
 * Token-inject screenshots for Career overlays (no browser CORS login needed).
 * Prefers BASE=http://localhost:3000 (must be in app.cors.allowed-origins).
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
const PORT = 9511
const userData = path.join(ROOT, 'tmp-edge-docs')
const BASE = process.env.BASE || 'http://localhost:3000'

async function cdp(ws, method, params = {}, sessionId) {
  const id = (cdp._id = (cdp._id || 0) + 1)
  const msg = { id, method, params }
  if (sessionId) msg.sessionId = sessionId
  ws.send(JSON.stringify(msg))
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`CDP timeout: ${method}`)), 45000)
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
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

async function evalJs(ws, sessionId, expression) {
  const { result } = await cdp(ws, 'Runtime.evaluate', {
    expression, returnByValue: true, awaitPromise: true,
  }, sessionId)
  return result?.value
}

async function shot(ws, sessionId, name) {
  const data = await cdp(ws, 'Page.captureScreenshot', {
    format: 'png', fromSurface: true, captureBeyondViewport: false,
  }, sessionId)
  const outPath = path.join(OUT, name)
  await fs.writeFile(outPath, Buffer.from(data.data, 'base64'))
  console.log(`OK ${name} (${(await fs.stat(outPath)).size})`)
}

async function main() {
  await fs.mkdir(userData, { recursive: true })
  await fs.mkdir(OUT, { recursive: true })

  const loginRes = await fetch(
    'http://127.0.0.1:8123/api/session/login?username=' +
      encodeURIComponent('游客') +
      '&password=' +
      encodeURIComponent('workpilot-local'),
    { method: 'POST' },
  )
  const body = await loginRes.json()
  const data = body?.data || body
  const token = data?.token || data?.accessToken || ''
  const refreshToken = data?.refreshToken || data?.refreshJwt || ''
  const userId = data?.userId || ''
  if (!token) throw new Error('login failed: ' + JSON.stringify(body))
  console.log('auth ok', { tokenLen: token.length, userId: !!userId, refresh: !!refreshToken })

  const edge = spawn(EDGE, [
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${userData}`,
    '--headless=new',
    '--disable-gpu',
    '--disable-extensions',
    '--no-first-run',
    '--no-default-browser-check',
    '--hide-scrollbars',
    'about:blank',
  ], { stdio: 'ignore' })

  for (let i = 0; i < 40; i++) {
    try { await fetchJson(`http://127.0.0.1:${PORT}/json/version`); break } catch { await sleep(250) }
  }
  const version = await fetchJson(`http://127.0.0.1:${PORT}/json/version`)
  const ws = new WebSocket(version.webSocketDebuggerUrl)
  await new Promise((r, j) => {
    ws.addEventListener('open', r, { once: true })
    ws.addEventListener('error', j, { once: true })
  })

  const { targetId } = await cdp(ws, 'Target.createTarget', { url: 'about:blank' })
  const { sessionId } = await cdp(ws, 'Target.attachToTarget', { targetId, flatten: true })
  await cdp(ws, 'Emulation.setDeviceMetricsOverride', {
    width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
  }, sessionId)
  await cdp(ws, 'Page.enable', {}, sessionId)

  // Prevent axios 401 from hard-navigating away during capture
  await cdp(ws, 'Page.addScriptToEvaluateOnNewDocument', {
    source: `
localStorage.setItem('token', ${JSON.stringify(token)});
localStorage.setItem('refreshToken', ${JSON.stringify(refreshToken)});
localStorage.setItem('userId', ${JSON.stringify(userId)});
localStorage.setItem('username', ${JSON.stringify(data?.username || '游客')});
localStorage.setItem('role', ${JSON.stringify(data?.role || 'GUEST')});
window.__WP_CAPTURE = true;
const _href = Object.getOwnPropertyDescriptor(Location.prototype, 'href');
Object.defineProperty(Location.prototype, 'href', {
  configurable: true,
  get() { return _href.get.call(this); },
  set(v) {
    if (window.__WP_CAPTURE && String(v).includes('/login')) {
      console.warn('[capture] blocked login redirect', v);
      return;
    }
    return _href.set.call(this, v);
  }
});
`,
  }, sessionId)

  // Login page (unauthenticated look — clear after capture of login separately)
  // Capture authenticated pages in one session:
  await cdp(ws, 'Page.navigate', { url: `${BASE}/` }, sessionId)
  await sleep(2800)
  await shot(ws, sessionId, 'screenshot-home.png')

  await cdp(ws, 'Page.navigate', { url: `${BASE}/chat/career` }, sessionId)
  await sleep(4500)
  for (let i = 0; i < 30; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.chat-core')`)) break
    await sleep(400)
  }
  const career = await evalJs(ws, sessionId, `({
    href: location.href,
    hasCore: !!document.querySelector('.chat-core'),
    hasPill: !!document.querySelector('.top-pill.companion'),
    token: !!localStorage.getItem('token'),
  })`)
  console.log('career', career)
  await shot(ws, sessionId, 'screenshot-career.png')

  await evalJs(ws, sessionId, `document.querySelector('.top-pill.companion')?.click()`)
  for (let i = 0; i < 30; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.overlay-badge.companion')`)) break
    await sleep(250)
  }
  console.log('companion', await evalJs(ws, sessionId, `({
    title: document.querySelector('.overlay-panel h2')?.textContent || '',
    badge: document.querySelector('.overlay-badge.companion')?.textContent || '',
    href: location.href,
  })`))
  await sleep(700)
  await shot(ws, sessionId, 'screenshot-companion.png')
  await evalJs(ws, sessionId, `document.querySelector('.overlay-close')?.click()`)
  await sleep(600)

  await evalJs(ws, sessionId, `document.querySelector('.top-pill.employee')?.click()`)
  for (let i = 0; i < 30; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.overlay-badge.employee')`)) break
    await sleep(250)
  }
  console.log('employee', await evalJs(ws, sessionId, `({
    title: document.querySelector('.overlay-panel h2')?.textContent || '',
    badge: document.querySelector('.overlay-badge.employee')?.textContent || '',
    href: location.href,
  })`))
  await sleep(900)
  await shot(ws, sessionId, 'screenshot-digital-employee.png')

  await cdp(ws, 'Page.navigate', { url: `${BASE}/chat/super` }, sessionId)
  await sleep(3000)
  await shot(ws, sessionId, 'screenshot-super.png')
  await cdp(ws, 'Page.navigate', { url: `${BASE}/knowledge` }, sessionId)
  await sleep(3000)
  await shot(ws, sessionId, 'screenshot-knowledge.png')

  // Login page without auth
  await evalJs(ws, sessionId, `window.__WP_CAPTURE = false; localStorage.clear();`)
  await cdp(ws, 'Page.navigate', { url: `${BASE}/login` }, sessionId)
  await sleep(2500)
  await shot(ws, sessionId, 'screenshot-login.png')

  await cdp(ws, 'Target.closeTarget', { targetId })
  ws.close()
  edge.kill()
  console.log('done')
}

main().catch((e) => { console.error(e); process.exit(1) })
