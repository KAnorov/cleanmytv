import { app, BrowserWindow, ipcMain, session, shell } from 'electron'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { configurePaths, paths } from './paths'
import { setSinks } from './log'
import * as T from './tv'
import * as debloat from './debloat'
import * as launcher from './launcher'
import * as theme from './theme'
import * as system from './system'
import * as tools from './toolchain'
import { localSubnets, scan } from './scan'
import { reconnect } from './reconnect'
import * as web from './web'
import type { CheckItem, ConnStatus, CytvApi } from '@shared/api'

let win: BrowserWindow | null = null

interface Settings {
  lastHost?: string
  /** The TV that answered there, to notice a different device at the same address. */
  lastDevice?: string
  lastModel?: string
}

function readSettings(): Settings {
  try { return JSON.parse(readFileSync(paths.settings, 'utf8')) } catch { return {} }
}

function writeSettings(s: Settings): void {
  mkdirSync(paths.data, { recursive: true })
  writeFileSync(paths.settings, JSON.stringify(s, null, 2))
}

async function checks(): Promise<CheckItem[]> {
  const st = T.getStatus()
  const connected = st.state === 'connected'
  const hasApk = existsSync(paths.bundledApk) || existsSync(paths.localApk)
  const jdk = tools.javaHome()
  return [
    {
      id: 'network', label: 'Локальная сеть',
      ...(localSubnets().length
        ? { status: 'ok', detail: `Компьютер в сети ${localSubnets().map((n) => n + '.x').join(', ')}.` }
        : { status: 'missing', detail: 'Домашняя сеть не найдена. Подключитесь к той же Wi-Fi или Ethernet, что и TV.' })
    },
    {
      id: 'tv', label: 'TV с включённой отладкой по сети',
      ...(connected
        ? { status: 'ok', detail: `${st.probe?.manufacturer} ${st.probe?.model} по адресу ${st.host}` }
        : { status: 'missing', detail: 'Включите на TV «Для разработчиков» и «Отладку по USB», затем найдите его ниже.' })
    },
    {
      id: 'auth', label: 'Компьютер разрешён на TV',
      ...(connected
        ? { status: 'ok', detail: 'TV принял этот компьютер.' }
        : st.state === 'waiting-auth'
          ? { status: 'checking', detail: 'Выберите «Разрешить» на TV.' }
          : { status: 'missing', detail: 'TV спросит один раз при первом подключении.' })
    },
    {
      id: 'apk', label: 'Чистый лаунчер',
      ...(hasApk
        ? { status: 'ok', detail: existsSync(paths.bundledApk) ? 'Встроен в приложение, скачивать не нужно.' : 'Локальная сборка готова.' }
        : { status: 'missing', detail: 'Не входит в эту копию. Соберите из исходников (шаг «Лаунчер»).' })
    },
    {
      id: 'jdk', label: 'Java 17 (только для сборки из исходников)',
      ...(jdk ? { status: 'ok', detail: jdk } : { status: 'optional', detail: 'Около 190 МБ. Не нужно при встроенном лаунчере.', download: 'jdk' })
    },
    {
      id: 'sdk', label: 'Android SDK 34 (только для сборки из исходников)',
      ...(tools.sdkReady() ? { status: 'ok', detail: tools.dirs.sdk } : { status: 'optional', detail: 'Около 250 МБ, сначала нужна Java 17.', download: 'sdk' })
    },
    {
      id: 'source', label: 'Исходники лаунчера (только для сборки)',
      ...(tools.sourceReady() ? { status: 'ok', detail: tools.dirs.source } : { status: 'optional', detail: 'Актуальный код cleanyourtv с GitHub, около 7 МБ.', download: 'source' })
    },
    {
      id: 'platform-tools', label: 'Google adb (только для сопряжения по Wi-Fi)',
      ...(tools.platformToolsReady() ? { status: 'ok', detail: tools.adbPath() } : { status: 'optional', detail: 'Около 15 МБ. Некоторым Google TV нужен одноразовый код сопряжения.', download: 'platform-tools' })
    }
  ] as CheckItem[]
}

type Handlers = { [K in keyof CytvApi as CytvApi[K] extends (...a: never[]) => Promise<unknown> ? K : never]: CytvApi[K] }

const handlers: Omit<Handlers, 'onLog' | 'onProgress' | 'onStatus'> = {
  scan: () => scan(),
  connect: async (host) => {
    const s = await T.connect(host)
    if (s.state === 'connected') writeSettings({ ...readSettings(), lastHost: host.trim(), lastDevice: s.probe?.device, lastModel: s.probe?.model })
    return s
  },
  // Only the saved address; T.connect, not handlers.connect, so a wrong device is never saved.
  reconnect: () => reconnect(readSettings(), { connect: T.connect, disconnect: T.disconnect, report: T.report }),
  disconnect: async () => T.disconnect(),
  status: async () => T.getStatus(),
  lastHost: async () => readSettings().lastHost ?? null,
  checkAdbClosed: (host) => system.adbClosed(host),
  pair: async (host, pairPort, code, connectPort) => {
    const r = await tools.pair(host, pairPort, code, connectPort)
    if (r.ok) await handlers.connect(host)
    return r
  },
  checks,
  download: (tool) => tools.get(tool),
  lists: async () => debloat.lists(T.getStatus().probe),
  plan: (id) => debloat.plan(id),
  applyDebloat: (req) => debloat.apply(req),
  restore: (id) => debloat.restore(id),
  launcherStatus: () => launcher.status(),
  install: (req) => launcher.install(req),
  buildLocal: () => tools.buildLocal(),
  useStockLauncher: () => launcher.useStock(),
  apps: () => theme.apps(),
  readTheme: () => theme.read(),
  applyTheme: (req) => theme.apply(req),
  resetTheme: () => theme.reset(),
  presets: async () => theme.presets(),
  screenshot: () => theme.screenshot(),
  webGet: (url) => web.get(String(url)),
  systemState: () => system.state(),
  setClockScreensaver: (on) => system.clockScreensaver(on),
  setPrivateDns: (host) => system.privateDns(host),
  setAnimationScale: (s) => system.animationScale(s),
  setLauncherLocale: (tag) => system.launcherLocale(tag),
  key: (code) => system.key(code),
  reboot: () => system.reboot()
}

function send(channel: string, value: unknown): void {
  if (win && !win.isDestroyed()) win.webContents.send(channel, value)
}

// The dev server URL is honoured only in a dev run; an installed app always loads its own files.
const devUrl = !app.isPackaged ? process.env.ELECTRON_RENDERER_URL : undefined
const appPage = (): string => pathToFileURL(join(__dirname, '../renderer/index.html')).href

/** The bridge controls the TV, so only the app's own page may use it: never a navigated-to or framed page. */
function isAppUrl(url: string | undefined): boolean {
  if (!url) return false
  const bare = url.split('#')[0].split('?')[0]
  return devUrl ? new URL(url).origin === new URL(devUrl).origin : bare === appPage()
}

function createWindow(): void {
  win = new BrowserWindow({
    width: 1240,
    height: 820,
    minWidth: 960,
    minHeight: 640,
    title: 'CleanMyTV',
    backgroundColor: '#FFFFFF',
    show: false,
    webPreferences: { preload: join(__dirname, '../preload/index.js'), sandbox: true, contextIsolation: true, nodeIntegration: false, webviewTag: false }
  })
  // Nothing in the app navigates; links open in the system browser instead (below).
  win.webContents.on('will-navigate', (e, url) => { if (!isAppUrl(url)) e.preventDefault() })
  win.webContents.on('will-redirect', (e, url) => { if (!isAppUrl(url)) e.preventDefault() })
  win.webContents.on('will-attach-webview', (e) => e.preventDefault())
  win.once('ready-to-show', () => win?.show())
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })
  if (devUrl) win.loadURL(devUrl)
  else win.loadFile(join(__dirname, '../renderer/index.html'))
}

// One copy at a time: two would race on the key file and on the same TV connection.
const primary = app.requestSingleInstanceLock()
if (!primary) app.quit()
app.on('second-instance', () => {
  if (win) { if (win.isMinimized()) win.restore(); win.focus() }
})

app.whenReady().then(() => {
  if (!primary) return
  // The app needs no camera, microphone, notifications or other web permissions.
  session.defaultSession.setPermissionRequestHandler((_wc, _perm, cb) => cb(false))
  // Packaged: lists, themes and the APK sit in Contents/Resources (mac) or resources\ (Windows).
  configurePaths(app.isPackaged ? process.resourcesPath : join(app.getAppPath(), 'resources'), app.getPath('userData'))
  setSinks((line) => send('log', line), (p) => send('progress', p))
  T.init(paths.adbKey, (s: ConnStatus) => send('status', s))
  for (const [name, fn] of Object.entries(handlers)) {
    ipcMain.handle(name, async (e, ...args: unknown[]) => {
      if (!isAppUrl(e.senderFrame?.url)) throw new Error('заблокировано: запрос пришёл не из окна приложения')
      try {
        return await (fn as (...a: unknown[]) => Promise<unknown>)(...args)
      } catch (e) {
        send('log', { task: name, level: 'error', text: (e as Error).message, time: Date.now() })
        throw e
      }
    })
  }
  createWindow()
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow() })
})

app.on('window-all-closed', () => {
  T.disconnect()
  if (process.platform !== 'darwin') app.quit()
})
