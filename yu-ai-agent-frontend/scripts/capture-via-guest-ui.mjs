/**
 * Capture via real guest login button (most reliable auth).
 * Usage: BASE=http://[::1]:3001 node scripts/capture-via-guest-ui.mjs
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
const PORT = 9499
const userData = path.join(ROOT, 'tmp-edge-cdp-guest')
const BASE = process.env.BASE || 'http://[::1]:3001'

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
  const shot = await cdp(ws, 'Page.captureScreenshot', {
    format: 'png', fromSurface: true, captureBeyondViewport: false,
  }, sessionId)
  const outPath = path.join(OUT, name)
  await fs.writeFile(outPath, Buffer.from(shot.data, 'base64'))
  console.log(`OK ${name} (${(await fs.stat(outPath)).size})`)
}

async function main() {
  await fs.mkdir(userData, { recursive: true })
  await fs.mkdir(OUT, { recursive: true })

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

  // Clear any leftover auth, land on login
  await cdp(ws, 'Page.addScriptToEvaluateOnNewDocument', {
    source: `['token','refreshToken','userId','username','role'].forEach(k=>localStorage.removeItem(k));`,
  }, sessionId)
  await cdp(ws, 'Page.navigate', { url: `${BASE}/login` }, sessionId)
  await sleep(2500)
  await shot(ws, sessionId, 'screenshot-login.png')

  // Click guest login
  const guestClick = await evalJs(ws, sessionId, `(() => {
    const btn = [...document.querySelectorAll('button')].find(b => (b.innerText||'').includes('游客'));
    if (!btn) return { ok:false, buttons: [...document.querySelectorAll('button')].map(b=>b.innerText.trim()).slice(0,8) };
    btn.click();
    return { ok:true, text: btn.innerText.trim() };
  })()`)
  console.log('guestClick', guestClick)

  // Wait for redirect away from login
  for (let i = 0; i < 40; i++) {
    const href = await evalJs(ws, sessionId, `location.href`)
    const token = await evalJs(ws, sessionId, `localStorage.getItem('token')`)
    if (href && !href.includes('/login') && token) {
      console.log('logged in', href, 'tokenLen', String(token).length)
      break
    }
    await sleep(400)
  }

  // Home
  await cdp(ws, 'Page.navigate', { url: `${BASE}/` }, sessionId)
  await sleep(3000)
  await shot(ws, sessionId, 'screenshot-home.png')

  // Career
  await cdp(ws, 'Page.navigate', { url: `${BASE}/chat/career` }, sessionId)
  await sleep(4500)
  for (let i = 0; i < 25; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.chat-core')`)) break
    await sleep(400)
  }
  console.log('career', await evalJs(ws, sessionId, `({href:location.href, hasCore:!!document.querySelector('.chat-core'), text:(document.body.innerText||'').slice(0,100)})`))
  await shot(ws, sessionId, 'screenshot-career.png')

  // Companion overlay
  await evalJs(ws, sessionId, `(() => {
    const el = document.querySelector('.top-pill.companion') || document.querySelector('button[title="个人伙伴设置"]');
    el && el.click();
  })()`)
  for (let i = 0; i < 25; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.overlay-badge.companion')`)) break
    await sleep(250)
  }
  console.log('companion overlay', await evalJs(ws, sessionId, `document.querySelector('.overlay-panel h2')?.textContent || location.href`))
  await sleep(600)
  await shot(ws, sessionId, 'screenshot-companion.png')
  await evalJs(ws, sessionId, `document.querySelector('.overlay-close')?.click()`)
  await sleep(500)

  // Digital employee overlay
  await evalJs(ws, sessionId, `(() => {
    const el = document.querySelector('.top-pill.employee') || document.querySelector('button[title="数字员工"]');
    el && el.click();
  })()`)
  for (let i = 0; i < 25; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.overlay-badge.employee')`)) break
    await sleep(250)
  }
  console.log('employee overlay', await evalJs(ws, sessionId, `document.querySelector('.overlay-panel h2')?.textContent || location.href`))
  await sleep(800)
  await shot(ws, sessionId, 'screenshot-digital-employee.png')

  // Super + knowledge (quick)
  await cdp(ws, 'Page.navigate', { url: `${BASE}/chat/super` }, sessionId)
  await sleep(3000)
  await shot(ws, sessionId, 'screenshot-super.png')
  await cdp(ws, 'Page.navigate', { url: `${BASE}/knowledge` }, sessionId)
  await sleep(3000)
  await shot(ws, sessionId, 'screenshot-knowledge.png')

  await cdp(ws, 'Target.closeTarget', { targetId })
  ws.close()
  edge.kill()
  console.log('done')
}

main().catch((e) => { console.error(e); process.exit(1) })
