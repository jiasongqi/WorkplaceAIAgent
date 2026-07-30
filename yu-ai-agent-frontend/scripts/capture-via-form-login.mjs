/**
 * Capture screenshots after form login (游客 / workpilot-local).
 * Usage: BASE=http://[::1]:3001 node scripts/capture-via-form-login.mjs
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
const PORT = 9501
const userData = path.join(ROOT, 'tmp-edge-cdp-form')
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

  await cdp(ws, 'Page.navigate', { url: `${BASE}/login` }, sessionId)
  await sleep(2500)
  await shot(ws, sessionId, 'screenshot-login.png')

  const loginRes = await evalJs(ws, sessionId, `(() => {
    const user = document.querySelector('#login-username');
    const pass = document.querySelector('#login-password');
    if (!user || !pass) return { ok:false, reason:'missing-inputs' };
    const setVal = (el, v) => {
      const proto = window.HTMLInputElement.prototype;
      const desc = Object.getOwnPropertyDescriptor(proto, 'value');
      desc.set.call(el, v);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    };
    setVal(user, '游客');
    setVal(pass, 'workpilot-local');
    const form = document.querySelector('form.auth-panel');
    if (form) {
      form.requestSubmit ? form.requestSubmit() : form.dispatchEvent(new Event('submit', { bubbles:true, cancelable:true }));
    } else {
      document.querySelector('button.btn-primary')?.click();
    }
    return { ok:true, user: user.value, passLen: pass.value.length };
  })()`)
  console.log('formLogin', loginRes)

  for (let i = 0; i < 50; i++) {
    const st = await evalJs(ws, sessionId, `({
      href: location.href,
      token: !!(localStorage.getItem('token')),
      refresh: !!(localStorage.getItem('refreshToken')),
    })`)
    if (st?.token && st.href && !st.href.includes('/login')) {
      console.log('logged in', st)
      break
    }
    await sleep(400)
  }

  await cdp(ws, 'Page.navigate', { url: `${BASE}/` }, sessionId)
  await sleep(3000)
  await shot(ws, sessionId, 'screenshot-home.png')

  await cdp(ws, 'Page.navigate', { url: `${BASE}/chat/career` }, sessionId)
  await sleep(4500)
  for (let i = 0; i < 30; i++) {
    const ok = await evalJs(ws, sessionId, `!!document.querySelector('.chat-core')`)
    if (ok) break
    await sleep(400)
  }
  const career = await evalJs(ws, sessionId, `({
    href: location.href,
    hasCore: !!document.querySelector('.chat-core'),
    hasPill: !!document.querySelector('.top-pill.companion'),
    text: (document.body.innerText||'').slice(0,120)
  })`)
  console.log('career', career)
  if (!career?.hasCore) throw new Error('career page not ready: ' + JSON.stringify(career))
  await shot(ws, sessionId, 'screenshot-career.png')

  await evalJs(ws, sessionId, `document.querySelector('.top-pill.companion')?.click()`)
  for (let i = 0; i < 30; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.overlay-badge.companion')`)) break
    await sleep(250)
  }
  console.log('companion', await evalJs(ws, sessionId, `document.querySelector('.overlay-panel h2')?.textContent`))
  await sleep(700)
  await shot(ws, sessionId, 'screenshot-companion.png')
  await evalJs(ws, sessionId, `document.querySelector('.overlay-close')?.click()`)
  await sleep(500)

  await evalJs(ws, sessionId, `document.querySelector('.top-pill.employee')?.click()`)
  for (let i = 0; i < 30; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.overlay-badge.employee')`)) break
    await sleep(250)
  }
  console.log('employee', await evalJs(ws, sessionId, `document.querySelector('.overlay-panel h2')?.textContent`))
  await sleep(900)
  await shot(ws, sessionId, 'screenshot-digital-employee.png')

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
