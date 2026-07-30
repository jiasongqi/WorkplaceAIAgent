/**
 * Capture overlays in a single Edge CDP session (avoids auth race).
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
const PORT = 9488
const userData = path.join(ROOT, 'tmp-edge-cdp-one')
const BASE = process.env.BASE || 'http://[::1]:3000'

async function cdp(ws, method, params = {}, sessionId) {
  const id = (cdp._id = (cdp._id || 0) + 1)
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
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

async function evalJs(ws, sessionId, expression) {
  const { result } = await cdp(ws, 'Runtime.evaluate', { expression, returnByValue: true }, sessionId)
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
  await new Promise((r, j) => { ws.addEventListener('open', r, { once: true }); ws.addEventListener('error', j, { once: true }) })

  const loginRes = await fetch(
    'http://127.0.0.1:8123/api/session/login?username=' + encodeURIComponent('游客') +
      '&password=' + encodeURIComponent('workpilot-local'),
    { method: 'POST' },
  )
  const body = await loginRes.json()
  const data = body?.data || body
  const token = data?.token || data?.accessToken || ''
  console.log('tokenLen', token.length)

  const { targetId } = await cdp(ws, 'Target.createTarget', { url: 'about:blank' })
  const { sessionId } = await cdp(ws, 'Target.attachToTarget', { targetId, flatten: true })
  await cdp(ws, 'Emulation.setDeviceMetricsOverride', {
    width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
  }, sessionId)
  await cdp(ws, 'Page.enable', {}, sessionId)
  const refreshToken = data?.refreshToken || data?.refreshJwt || ''
  await cdp(ws, 'Page.addScriptToEvaluateOnNewDocument', {
    source: `localStorage.setItem('token', ${JSON.stringify(token)});
localStorage.setItem('refreshToken', ${JSON.stringify(refreshToken)});
localStorage.setItem('username','游客');
localStorage.setItem('role','GUEST');`,
  }, sessionId)
  await cdp(ws, 'Page.navigate', { url: `${BASE}/chat/career` }, sessionId)
  await sleep(5000)
  for (let i = 0; i < 30; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.chat-core')`)) break
    await sleep(400)
  }
  console.log('page', await evalJs(ws, sessionId, `({href:location.href, text:(document.body.innerText||'').slice(0,80)})`))

  await shot(ws, sessionId, 'screenshot-career.png')

  // Companion — open drawer
  const clickedCompanion = await evalJs(ws, sessionId, `(() => {
    const el = document.querySelector('.top-pill.companion')
      || document.querySelector('button[title="个人伙伴设置"]')
      || [...document.querySelectorAll('button')].find(b => (b.innerText||'').includes('我的职场伙伴') && b.className.includes('top-pill'));
    if (!el) return { ok:false };
    el.dispatchEvent(new MouseEvent('click', { bubbles:true, cancelable:true, view:window }));
    return { ok:true, text:(el.innerText||'').trim() };
  })()`)
  console.log('click companion', clickedCompanion)
  for (let i = 0; i < 25; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.overlay-badge.companion')`)) break
    await sleep(250)
  }
  console.log('companion', await evalJs(ws, sessionId, `({href:location.href, title:document.querySelector('.overlay-panel h2')?.textContent||'', badge:document.querySelector('.overlay-badge')?.textContent||''})`))
  await sleep(700)
  await shot(ws, sessionId, 'screenshot-companion.png')
  await evalJs(ws, sessionId, `document.querySelector('.overlay-close')?.dispatchEvent(new MouseEvent('click',{bubbles:true}))`)
  await sleep(600)

  // Digital employee
  const clickedEmp = await evalJs(ws, sessionId, `(() => {
    const el = document.querySelector('.top-pill.employee')
      || document.querySelector('button[title="数字员工"]')
      || [...document.querySelectorAll('button')].find(b => (b.innerText||'').includes('去创建') && b.className.includes('top-pill'));
    if (!el) return { ok:false };
    el.dispatchEvent(new MouseEvent('click', { bubbles:true, cancelable:true, view:window }));
    return { ok:true, text:(el.innerText||'').trim() };
  })()`)
  console.log('click employee', clickedEmp)
  for (let i = 0; i < 25; i++) {
    if (await evalJs(ws, sessionId, `!!document.querySelector('.overlay-badge.employee')`)) break
    await sleep(250)
  }
  console.log('employee', await evalJs(ws, sessionId, `({href:location.href, title:document.querySelector('.overlay-panel h2')?.textContent||'', badge:document.querySelector('.overlay-badge')?.textContent||''})`))
  await sleep(900)
  await shot(ws, sessionId, 'screenshot-digital-employee.png')

  await cdp(ws, 'Target.closeTarget', { targetId })
  ws.close()
  edge.kill()
  console.log('done')
}

main().catch((e) => { console.error(e); process.exit(1) })
