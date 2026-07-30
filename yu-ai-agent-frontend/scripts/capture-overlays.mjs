/**
 * Recapture companion / digital-employee overlay screenshots only.
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
const PORT = 9477
const userData = path.join(ROOT, 'tmp-edge-cdp-overlay')
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
  if (!res.ok) throw new Error(`HTTP ${res.status} ${url}`)
  return res.json()
}

async function evalJs(ws, sessionId, expression) {
  const { result } = await cdp(ws, 'Runtime.evaluate', {
    expression,
    returnByValue: true,
  }, sessionId)
  return result?.value
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
    '--disable-extensions',
    '--hide-scrollbars',
    'about:blank',
  ], { stdio: 'ignore' })

  for (let i = 0; i < 40; i++) {
    try {
      await fetchJson(`http://127.0.0.1:${PORT}/json/version`)
      break
    } catch {
      await sleep(250)
    }
  }

  const version = await fetchJson(`http://127.0.0.1:${PORT}/json/version`)
  const ws = new WebSocket(version.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true })
    ws.addEventListener('error', reject, { once: true })
  })

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
  console.log('BASE=', BASE, 'tokenLen=', token.length)

  const shots = [
    {
      name: 'screenshot-companion.png',
      clickSelectors: ['.top-pill.companion', '.side-team-item', '.cap-card'],
      waitSel: '.overlay-badge.companion',
    },
    {
      name: 'screenshot-digital-employee.png',
      clickSelectors: ['.top-pill.employee', '.cap-card:nth-child(2)'],
      waitSel: '.overlay-badge.employee',
    },
  ]

  for (const page of shots) {
    const { targetId } = await cdp(ws, 'Target.createTarget', { url: 'about:blank' })
    const { sessionId } = await cdp(ws, 'Target.attachToTarget', { targetId, flatten: true })
    await cdp(ws, 'Emulation.setDeviceMetricsOverride', {
      width: 1440, height: 900, deviceScaleFactor: 1, mobile: false,
    }, sessionId)
    await cdp(ws, 'Page.enable', {}, sessionId)
    await cdp(ws, 'Page.addScriptToEvaluateOnNewDocument', {
      source: `localStorage.setItem('token', ${JSON.stringify(token)});
localStorage.setItem('username', '游客');
localStorage.setItem('role', 'GUEST');`,
    }, sessionId)

    await cdp(ws, 'Page.navigate', { url: `${BASE}/chat/career` }, sessionId)
    await sleep(4500)
    for (let i = 0; i < 25; i++) {
      if (await evalJs(ws, sessionId, `!!document.querySelector('.chat-core')`)) break
      await sleep(400)
    }

    const clickInfo = await evalJs(ws, sessionId, `(() => {
      const sels = ${JSON.stringify(page.clickSelectors)};
      for (const sel of sels) {
        const el = document.querySelector(sel);
        if (el) {
          el.click();
          return { ok: true, sel, text: (el.innerText || '').trim().slice(0, 48) };
        }
      }
      return { ok: false };
    })()`)
    console.log('click', page.name, clickInfo)

    let visible = false
    for (let i = 0; i < 25; i++) {
      if (await evalJs(ws, sessionId, `!!document.querySelector(${JSON.stringify(page.waitSel)})`)) {
        visible = true
        break
      }
      await sleep(300)
    }
    console.log('overlay', page.name, visible)

    const probe = await evalJs(ws, sessionId, `(() => ({
      href: location.href,
      badge: document.querySelector('.overlay-badge')?.textContent || '',
      title: document.querySelector('.overlay-panel h2')?.textContent || '',
      hasLogin: !!document.querySelector('.login-page'),
    }))()`)
    console.log('probe', page.name, probe)

    await sleep(700)
    const shot = await cdp(ws, 'Page.captureScreenshot', {
      format: 'png', fromSurface: true, captureBeyondViewport: false,
    }, sessionId)
    const outPath = path.join(OUT, page.name)
    await fs.writeFile(outPath, Buffer.from(shot.data, 'base64'))
    console.log(`OK ${page.name} (${(await fs.stat(outPath)).size} bytes)`)
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
