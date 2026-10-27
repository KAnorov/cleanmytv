import { randomBytes } from 'node:crypto'
import { mkdirSync, mkdtempSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import * as T from '../src/main/tv'
import * as L from '../src/main/launcher'
import * as Th from '../src/main/theme'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-test-'))
process.env.HOME = home
// A resources folder of its own: the real lists and themes, and a stand-in APK, so this runs without
// the launcher having been built (CI builds it in a separate job).
const resources = join(home, 'resources')
mkdirSync(resources)
symlinkSync(resolve('resources/upstream'), join(resources, 'upstream'))
writeFileSync(join(resources, 'launcher.apk'), randomBytes(200 * 1024))
writeFileSync(join(resources, 'launcher.json'), JSON.stringify({ versionName: '2.4' }))
configurePaths(resources, join(home, 'data'))

describe('launcher and theme', () => {
  let tv: FakeTv
  let server: Server
  beforeAll(async () => {
    tv = newTv()
    tv.maxData = 256 * 1024
    for (const p of ['com.google.android.apps.tv.launcherx', 'com.netflix.ninja', 'com.google.android.youtube.tv']) tv.packages.set(p, 'enabled')
    tv.home = 'com.google.android.apps.tv.launcherx/.home.HomeActivity'
    tv.homeClaims = ['com.google.android.apps.tv.launcherx/.home.HomeActivity']
    tv.leanback = ['com.netflix.ninja', 'com.google.android.youtube.tv']
    let port: number
    ;({ server, port } = await startFakeAdbd(tv))
    T.init(join(home, 'data', 'adbkey'), () => {})
    expect((await T.connect(`127.0.0.1:${port}`)).state).toBe('connected')
  })
  afterAll(() => { T.disconnect(); server.close() })

  it('installs, starts, then hands Home over and disables the stock launcher', async () => {
    const r = await L.install({ apk: 'bundled', stockLauncher: 'auto', inputKeyService: true, homeKeyService: false })
    expect(r.summary).toMatch(/Installed/)
    expect(r.ok).toBe(true)
    expect(tv.home).toBe('com.gathin.tvhome/.MainActivity')
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('disabled')
    const order = tv.commands.filter((c) => /pm install|am start -W|pm disable-user|set-home-activity/.test(c))
    expect(order.map((c) => c.split(' ').slice(0, 2).join(' '))).toEqual(['pm install', 'am start', 'pm disable-user', 'cmd package'])
    expect(tv.commands.some((c) => c.includes('InputKeyService'))).toBe(true)
    const st = await L.status()
    expect(st.isHome).toBe(true)
    expect(st.versionName).toBe('2.4')
  })

  it('gives Home back to the stock launcher', async () => {
    const r = await L.useStock()
    expect(r.ok).toBe(true)
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('enabled')
  })

  it('pushes config.json and a wallpaper, then reads them back', async () => {
    const jpeg = Buffer.from('ffd8ffe000104a464946', 'hex')
    const r = await Th.apply({
      config: { dock_apps: ['com.google.android.youtube.tv', 'com.netflix.ninja'], columns: 5, wallpaper: true, wallpaper_dim: 30, greeting_name: '' },
      wallpaper: { kind: 'set', image: { name: 'wallpaper.jpg', base64: jpeg.toString('base64') } },
      wallpapers: { kind: 'keep' }
    })
    expect(r.ok).toBe(true)
    const back = await Th.read()
    expect(back.present).toBe(true)
    expect(back.config.dock_apps).toEqual(['com.google.android.youtube.tv', 'com.netflix.ninja'])
    expect(back.config.columns).toBe(5)
    expect(back.wallpaper).toBe(`data:image/jpeg;base64,${jpeg.toString('base64')}`)
    expect(tv.files.get('/sdcard/Android/data/com.gathin.tvhome/files/theme_revision')?.length).toBe(32)
    const apps = await Th.apps()
    expect(apps.map((a) => a.label)).toEqual(['Netflix', 'YouTube'])
  })

  it('opens the launcher when the TV will not let the shell create its folder', async () => {
    tv.appDir = 'missing'
    const before = tv.commands.length
    const r = await Th.apply({ config: { columns: 4 }, wallpaper: { kind: 'keep' }, wallpapers: { kind: 'keep' } })
    expect(r.ok).toBe(true)
    expect(tv.commands.slice(before).some((c) => c.startsWith('am start -W -n com.gathin.tvhome/.MainActivity'))).toBe(true)
  })

  it('says so plainly when the folder stays out of reach', async () => {
    tv.appDir = 'denied'
    await expect(Th.apply({ config: { columns: 4 }, wallpaper: { kind: 'keep' }, wallpapers: { kind: 'keep' } })).rejects.toThrow(/won't let the app write/)
    tv.appDir = 'ok'
  }, 15000)

  it('refuses wallpaper names with shell syntax', async () => {
    await expect(Th.apply({ config: {}, wallpaper: { kind: 'keep' }, wallpapers: { kind: 'set', images: [{ name: 'a;reboot.jpg', base64: '' }] } })).rejects.toThrow(/unsupported/)
  })

  it('ships the upstream themes as presets', () => {
    const ids = Th.presets().map((p) => p.id)
    expect(ids).toEqual(expect.arrayContaining(['aurora', 'default', 'six-columns']))
    expect(Th.presets().find((p) => p.id === 'aurora')?.wallpaper).toMatch(/^data:image\/jpeg;base64,/)
  })
})
