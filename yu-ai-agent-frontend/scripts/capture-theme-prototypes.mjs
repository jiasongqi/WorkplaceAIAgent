/**
 * Capture three theme variants from prototypes/theme-sage.html
 */
import { spawn } from 'node:child_process'
import { setTimeout as sleep } from 'node:timers/promises'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '../..')
const OUT = path.join(ROOT, 'docs/assets')
const HTML = path.join(ROOT, 'yu-ai-agent-frontend/prototypes/theme-sage.html')
const PAGE = pathToFileURL(HTML).href
const EDGE = process.env.EDGE_PATH ||
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const PORT = 9522
const userData = path.join(ROOT, 'tmp-edge-themes')

const THEMES = [
  { key: 'dark', file: 'screenshot-theme-dark.png', label: '原版暗色' },
  { key: 'capsule', file: 'screenshot-theme-capsule.png', label: '胶囊增强' },
  { key: 'sage', file: 'screenshot-theme-sage.png', label: '青荷绿' },
]

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
  const { result } = await cdp(ws, 'Runtime.evaluate', {
    expression, returnByValue: true, awaitPromise: true,
  }, sessionId)
  return result?.value
}

async function main() {
  await fs.mkdir(userData, { recursive: true })
  await fs.mkdir(OUT, { recursive: true })
  await fs.access(HTML)

  const edge = spawn(EDGE, [
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${userData}`,
    '--headless=new',
    '--disable-gpu',
    '--disable-extensions',
    '--allow-file-access-from-files',
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
  await cdp(ws, 'Page.navigate', { url: PAGE }, sessionId)
  await sleep(2000)

  for (const theme of THEMES) {
    const switched = await evalJs(ws, sessionId, `(() => {
      const btn = document.querySelector('.theme-btn[data-theme="${theme.key}"]');
      if (!btn) return { ok: false };
      btn.click();
      return {
        ok: true,
        attr: document.documentElement.getAttribute('data-theme'),
        name: document.getElementById('panelName')?.textContent || '',
      };
    })()`)
    console.log(theme.label, switched)
    await sleep(900)

    const shot = await cdp(ws, 'Page.captureScreenshot', {
      format: 'png', fromSurface: true, captureBeyondViewport: false,
    }, sessionId)
    const outPath = path.join(OUT, theme.file)
    await fs.writeFile(outPath, Buffer.from(shot.data, 'base64'))
    console.log(`OK ${theme.file} (${(await fs.stat(outPath)).size})`)
  }

  await cdp(ws, 'Target.closeTarget', { targetId })
  ws.close()
  edge.kill()
  console.log('done')
}

main().catch((e) => { console.error(e); process.exit(1) })
