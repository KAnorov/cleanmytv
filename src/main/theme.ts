// The launcher's config.json overlay and wallpapers on the TV: the port of upstream tools/theme.sh,
// plus reading the current theme back so the app edits what is really there.
import { randomUUID } from 'node:crypto'
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { paths } from './paths'
import { log } from './log'
import * as T from './tv'
import { PKG } from './launcher'
import type { ApplyResult, ImageFile, LauncherConfig, ThemeApplyRequest, ThemePreset, TvApp, TvTheme } from '@shared/api'

const TASK = 'theme'
const DIR = `/sdcard/Android/data/${PKG}/files`
const IMAGE_GLOB = ['jpg', 'jpeg', 'png', 'webp', 'JPG', 'JPEG', 'PNG', 'WEBP'].map((e) => `${DIR}/wallpapers/*.${e}`).join(' ')

// Restart Home so it rereads the theme. A force-stop drops the app's accessibility services from the
// enabled list (asynchronously), so put the list back if it had ours, as theme.sh does.
const RESTART = `a=$(settings get secure enabled_accessibility_services); am force-stop ${PKG}; am start -n ${PKG}/.MainActivity >/dev/null; case "$a" in *${PKG}*) sleep 1; settings put secure enabled_accessibility_services ''; settings put secure enabled_accessibility_services "$a"; settings put secure accessibility_enabled 1 ;; esac`

// Since Android 11 the shell may write inside Android/data/<pkg> but some TVs (TCL, #20) refuse to
// create that folder: only the app can, the first time it asks Android for it. So check by writing a
// file, and if that fails, open the launcher once so it makes the folder, then check again.
const WRITABLE = `mkdir -p ${DIR}/wallpapers 2>&1; touch ${DIR}/.cytv 2>&1 && rm -f ${DIR}/.cytv && echo writable`

async function ensureDir(): Promise<void> {
  let out = await T.sh(TASK, WRITABLE)
  for (let i = 0; i < 3 && !out.includes('writable'); i++) {
    if (i === 0) await T.sh(TASK, `am start -W -n ${PKG}/.MainActivity`)
    await new Promise((r) => setTimeout(r, 1500))
    out = await T.sh(TASK, WRITABLE)
  }
  if (!out.includes('writable')) {
    throw new Error(`This TV won't let the app write the launcher's settings folder (${DIR}). ` +
      'Press Home on the remote so the launcher opens, then try again. If it still fails, ' +
      'please report your TV model and Android version on GitHub.')
  }
}

export async function read(): Promise<TvTheme> {
  const raw = await T.pullB64(`${DIR}/config.json`)
  let config: LauncherConfig = {}
  if (raw) {
    try { config = JSON.parse(raw.toString('utf8').replace(/^﻿/, '')) } catch { config = {} }
  }
  const wall = await T.pullB64(`${DIR}/wallpaper.jpg`)
  const ls = await T.tv().shell(`ls ${DIR}/wallpapers 2>/dev/null`)
  return {
    config,
    present: !!raw,
    wallpaper: wall && wall.length ? `data:image/jpeg;base64,${wall.toString('base64')}` : null,
    wallpapers: ls.split(/\s+/).filter((n) => /^[A-Za-z0-9_.-]+\.(jpe?g|png|webp)$/i.test(n))
  }
}

function checkImageName(img: ImageFile): void {
  if (!/^[A-Za-z0-9_.-]+$/.test(img.name)) throw new Error(`unsupported wallpaper filename: ${img.name}`)
}

/** Drops keys the launcher would ignore anyway, so the file on the TV stays readable. */
function clean(config: LauncherConfig): LauncherConfig {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(config)) {
    if (v === undefined || v === null) continue
    if (typeof v === 'string' && v === '' && !['greeting_name', 'live_tv_package', 'vpn_app'].includes(k)) continue
    out[k] = v
  }
  return out as LauncherConfig
}

export async function apply(req: ThemeApplyRequest): Promise<ApplyResult> {
  const json = Buffer.from(JSON.stringify(clean(req.config), null, 2) + '\n')
  if (json.length > 64 * 1024) throw new Error('config.json would be over 64 KB; the launcher ignores files that large.')
  await ensureDir()
  await T.pushB64(TASK, json, `${DIR}/config.json`)
  if (req.wallpaper.kind === 'set') {
    await T.pushB64(TASK, Buffer.from(req.wallpaper.image.base64, 'base64'), `${DIR}/wallpaper.jpg`)
  } else if (req.wallpaper.kind === 'remove') {
    await T.sh(TASK, `rm -f ${DIR}/wallpaper.jpg`)
  }
  if (req.wallpapers.kind === 'set') {
    req.wallpapers.images.forEach(checkImageName)
    await T.sh(TASK, `rm -f ${IMAGE_GLOB}`)
    for (const img of req.wallpapers.images) {
      await T.pushB64(TASK, Buffer.from(img.base64, 'base64'), `${DIR}/wallpapers/${img.name}`)
    }
  }
  // A new revision makes the launcher clear on-TV column and motion choices, even for identical bytes.
  await T.sh(TASK, `printf '%s' '${randomUUID().replace(/-/g, '')}' > ${DIR}/theme_revision`)
  await T.sh(TASK, `${RESTART}; echo applied`)
  log(TASK, 'ok', 'Theme applied; Home restarted.')
  return { ok: true, summary: 'Applied. The TV restarted its home screen.', warnings: [] }
}

export async function reset(): Promise<ApplyResult> {
  await ensureDir()
  await T.sh(TASK, `rm -f ${DIR}/config.json ${DIR}/wallpaper.jpg ${IMAGE_GLOB}; printf '%s' '${randomUUID().replace(/-/g, '')}' > ${DIR}/theme_revision; ${RESTART}; echo reset`)
  return { ok: true, summary: 'Back to the built-in look.', warnings: [] }
}

const PRESET_TEXT: Record<string, [string, string]> = {
  default: ['Default', 'The built-in look: black, four columns, no wallpaper.'],
  aurora: ['Aurora', 'A dark aurora wallpaper, teal and violet on navy.'],
  dussehra: ['Dussehra', 'Festive skin: marigold toran, diya for Wi-Fi, bell for Inputs, petal shower.'],
  'six-columns': ['Six columns', 'Six smaller tiles per row and one grid row under the dock.']
}

export function presets(): ThemePreset[] {
  if (!existsSync(paths.themes)) return []
  return readdirSync(paths.themes).sort().flatMap((id) => {
    const cfg = join(paths.themes, id, 'config.json')
    if (!existsSync(cfg)) return []
    const wall = join(paths.themes, id, 'wallpaper.jpg')
    const [name, description] = PRESET_TEXT[id] ?? [id, '']
    return [{
      id,
      name,
      description,
      config: JSON.parse(readFileSync(cfg, 'utf8')),
      wallpaper: existsSync(wall) ? `data:image/jpeg;base64,${readFileSync(wall).toString('base64')}` : null
    }]
  })
}

// Names for apps people put on a TV; anything else gets a name made from its package.
const KNOWN: Record<string, string> = {
  'com.netflix.ninja': 'Netflix',
  'com.google.android.youtube.tv': 'YouTube',
  'com.google.android.youtube.tvkids': 'YouTube Kids',
  'com.google.android.youtube.tvmusic': 'YouTube Music',
  'com.amazon.amazonvideo.livingroom': 'Prime Video',
  'com.disney.disneyplus': 'Disney+',
  'in.startv.hotstar': 'JioHotstar',
  'com.spotify.tv.android': 'Spotify',
  'com.plexapp.android': 'Plex',
  'org.jellyfin.androidtv': 'Jellyfin',
  'org.xbmc.kodi': 'Kodi',
  'com.apple.atve.androidtv.appletv': 'Apple TV',
  'com.wbd.stream': 'Max',
  'com.hbo.hbonow': 'HBO Max',
  'com.hulu.livingroomplus': 'Hulu',
  'tv.twitch.android.app': 'Twitch',
  'com.google.android.videos': 'Google TV',
  'com.google.android.apps.youtube.unplugged': 'YouTube TV',
  'com.android.vending': 'Play Store',
  'com.android.tv.settings': 'Settings',
  'com.google.android.tvlauncher': 'Stock launcher',
  'com.google.android.apps.tv.launcherx': 'Google TV home',
  'com.mediatek.wwtv.tvcenter': 'Live TV',
  'com.sony.dtv.tvlin': 'Live TV',
  'com.stremio.one': 'Stremio',
  'com.peacocktv.peacockandroid': 'Peacock',
  'com.cbs.ott': 'Paramount+',
  'com.crunchyroll.crunchyroid': 'Crunchyroll',
  'com.bbc.iplayer.tv': 'BBC iPlayer',
  'air.ITVMobilePlayer': 'ITVX',
  'com.zee5.androidtv': 'ZEE5',
  'com.jio.media.stb.ondemand': 'JioCinema',
  'com.sonyliv': 'Sony LIV',
  'com.fancode.tv': 'FanCode',
  'com.globo.globotv': 'Globoplay',
  'com.vrt.vrtmax.androidtv': 'VRT MAX',
  'tv.pluto.android': 'Pluto TV',
  'com.tubitv': 'Tubi',
  'com.android.chrome': 'Chrome',
  'com.amazon.music.tv': 'Amazon Music',
  'com.apple.android.music': 'Apple Music',
  'com.google.android.apps.mediashell': 'Chromecast',
  'com.teamsmart.videomanager.tv': 'SmartTube',
  'com.liskovsoft.smarttubetv.beta': 'SmartTube',
  'com.cetusplay.remoteservice': 'CetusPlay',
  'com.vanced.android.youtube': 'YouTube Vanced',
  'com.mxtech.videoplayer.ad': 'MX Player',
  'org.videolan.vlc': 'VLC'
}

export function labelFor(pkg: string): string {
  if (KNOWN[pkg]) return KNOWN[pkg]
  const parts = pkg.split('.').filter((p) => !['com', 'org', 'net', 'tv', 'android', 'app', 'apps', 'androidtv', 'atv', 'in', 'co', 'www', 'livingroom', 'ott', 'mobile'].includes(p.toLowerCase()))
  const word = parts[parts.length > 1 ? 1 : 0] ?? pkg.split('.').pop() ?? pkg
  return word.replace(/[_-]+/g, ' ').replace(/([a-z])([A-Z])/g, '$1 $2').replace(/\b\w/g, (c) => c.toUpperCase())
}

function packagesIn(out: string): Set<string> {
  const s = new Set<string>()
  for (const line of out.split('\n')) {
    const m = /^\s*([A-Za-z0-9_.]+)\/\S+/.exec(line)
    if (m) s.add(m[1])
  }
  return s
}

/** Apps the launcher can show as tiles: TV (leanback) apps, then ordinary launcher apps. */
export async function apps(): Promise<TvApp[]> {
  const tvOut = await T.tv().shell('cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER')
  const phoneOut = await T.tv().shell('cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER')
  const tvPkgs = packagesIn(tvOut)
  const phonePkgs = packagesIn(phoneOut)
  const skip = new Set([PKG, 'com.android.tv.settings', 'com.android.settings'])
  const all: TvApp[] = []
  for (const pkg of tvPkgs) if (!skip.has(pkg)) all.push({ pkg, label: labelFor(pkg), tv: true })
  for (const pkg of phonePkgs) if (!skip.has(pkg) && !tvPkgs.has(pkg)) all.push({ pkg, label: labelFor(pkg), tv: false })
  return all.sort((a, b) => Number(b.tv) - Number(a.tv) || a.label.localeCompare(b.label))
}

export async function screenshot(): Promise<string> {
  const f = '/data/local/tmp/cytv-shot.png'
  await T.tv().shell(`screencap -p ${f}`, 30000)
  const png = await T.pullB64(f)
  await T.tv().shell(`rm -f ${f}`)
  if (!png || png.length < 100) throw new Error('The TV returned an empty screenshot. Live TV, HDMI and some streaming apps block capture: press Home and try again.')
  return `data:image/png;base64,${png.toString('base64')}`
}
