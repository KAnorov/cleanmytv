import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { ApplyResult, LauncherConfig, ThemeApplyRequest, ThemePreset, TvApp, TvTheme } from '@shared/api'
import type { ScreenProps } from '../App'
import { AppOrder } from '../components/AppOrder'
import { TvPreview } from '../components/TvPreview'
import { Busy, Confirm, Modal, ResultNotice, Slider, Toggle, errorText } from '../components/ui'
import { BUILTIN, orderedApps, val } from '../lib/apps'
import { dataUrlToImage, prepareWallpaper, uniqueNames, type PreparedImage } from '../lib/image'
import { fetchBingDaily, tmdbBackdropPaths, fetchTmdbBackdrop } from '../lib/backgrounds'

type Tab = 'apps' | 'layout' | 'backdrop' | 'greeting' | 'motion' | 'advanced'
type WallChange = { kind: 'keep' } | { kind: 'remove' } | { kind: 'set'; image: PreparedImage }
type RotationChange = { kind: 'keep' } | { kind: 'set'; images: PreparedImage[] }

const TABS: [Tab, string][] = [
  ['apps', 'Приложения'],
  ['layout', 'Макет'],
  ['backdrop', 'Фон'],
  ['greeting', 'Приветствие'],
  ['motion', 'Анимация'],
  ['advanced', 'Прочее']
]

export function Customise({ markDone }: ScreenProps): ReactNode {
  const [apps, setApps] = useState<TvApp[] | null>(null)
  const [loaded, setLoaded] = useState<TvTheme | null>(null)
  const [cfg, setCfg] = useState<LauncherConfig>({})
  const [wall, setWall] = useState<WallChange>({ kind: 'keep' })
  const [rotation, setRotation] = useState<RotationChange>({ kind: 'keep' })
  const [presets, setPresets] = useState<ThemePreset[]>([])
  const [tab, setTab] = useState<Tab>('apps')
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [confirmReset, setConfirmReset] = useState(false)
  const [shot, setShot] = useState<string | null>(null)

  async function load(): Promise<void> {
    setLoadError(null)
    try {
      const [a, t] = await Promise.all([window.cytv.apps(), window.cytv.readTheme()])
      setApps(a)
      setLoaded(t)
      setCfg(structuredClone(t.config))
      setWall({ kind: 'keep' })
      setRotation({ kind: 'keep' })
    } catch (e) {
      setLoadError(errorText(e))
    }
  }

  useEffect(() => {
    load()
    window.cytv.presets().then(setPresets, () => setPresets([]))
  }, [])

  const set = <K extends keyof LauncherConfig>(key: K, value: LauncherConfig[K] | undefined): void =>
    setCfg((prev) => {
      const next = { ...prev }
      if (value === undefined) delete next[key]
      else next[key] = value
      return next
    })

  const dirty =
    !!loaded && (JSON.stringify(sortKeys(cfg)) !== JSON.stringify(sortKeys(loaded.config)) || wall.kind !== 'keep' || rotation.kind !== 'keep')

  const wallpaperUrl = wall.kind === 'set' ? wall.image.dataUrl : wall.kind === 'remove' ? null : (loaded?.wallpaper ?? null)
  const firstRotation = rotation.kind === 'set' ? rotation.images[0]?.dataUrl : undefined
  const previewWall = wallpaperUrl ?? (val(cfg, 'wallpaper_rotation_minutes') > 0 ? (firstRotation ?? null) : null)

  async function apply(): Promise<void> {
    setBusy('Отправляем тему и перезапускаем Home на TV')
    setResult(null)
    const req: ThemeApplyRequest = {
      config: cfg,
      wallpaper: wall.kind === 'set' ? { kind: 'set', image: { name: 'wallpaper.jpg', base64: wall.image.base64 } } : wall,
      wallpapers: rotation.kind === 'set' ? { kind: 'set', images: rotation.images.map(({ name, base64 }) => ({ name, base64 })) } : rotation
    }
    try {
      const r = await window.cytv.applyTheme(req)
      setResult(r)
      if (r.ok) {
        markDone('customise')
        await load()
      }
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  async function reset(): Promise<void> {
    setConfirmReset(false)
    setBusy('Удаляем тему с TV')
    try {
      setResult(await window.cytv.resetTheme())
      await load()
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  async function screenshot(): Promise<void> {
    setBusy('Делаем снимок экрана TV')
    try {
      setShot(await window.cytv.screenshot())
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  if (loadError)
    return (
      <div className="content">
        <h1>Настройка</h1>
        <div className="notice error">{loadError}</div>
        <button className="btn" onClick={load}>
          Попробовать снова
        </button>
      </div>
    )
  if (!apps || !loaded)
    return (
      <div className="content">
        <h1>Настройка</h1>
        <Busy text="Читаем приложения и текущую тему с TV" />
      </div>
    )

  return (
    <>
      <div className="content with-footer">
        <h1>Настройка</h1>
        <p className="lede">
          Изменения сразу видны в предпросмотре и попадут на TV после нажатия «Применить». Пересборка не нужна: лаунчер
          прочитает их при следующем запуске Home. Настройка, которую вы не трогали, остаётся встроенной.
        </p>
        <ResultNotice result={result} onDismiss={() => setResult(null)} />

        <div className="custom">
          <div>
            <div className="tabs" role="tablist">
              {TABS.map(([id, label]) => (
                <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? 'on' : ''} onClick={() => setTab(id)}>
                  {label}
                </button>
              ))}
            </div>
            {tab === 'apps' && <AppsTab apps={apps} cfg={cfg} set={set} />}
            {tab === 'layout' && <LayoutTab cfg={cfg} set={set} />}
            {tab === 'backdrop' && (
              <BackdropTab
                cfg={cfg}
                set={set}
                setCfg={setCfg}
                wallpaperUrl={wallpaperUrl}
                wall={wall}
                setWall={setWall}
                rotation={rotation}
                setRotation={setRotation}
                loadedRotation={loaded.wallpapers}
                presets={presets}
              />
            )}
            {tab === 'greeting' && <GreetingTab cfg={cfg} set={set} />}
            {tab === 'motion' && <MotionTab cfg={cfg} set={set} />}
            {tab === 'advanced' && <AdvancedTab apps={apps} cfg={cfg} set={set} />}
          </div>
          <div className="custom-preview">
            <TvPreview apps={apps} cfg={cfg} wallpaper={previewWall} />
            <p className="preview-caption">
              Эскиз Home с вашими настройками. На плитках показаны инициалы, потому что приложение не может прочитать
              иконки приложений с TV. Нажмите на плитку дока, чтобы выделить её. Чтобы увидеть реальный экран, сделайте
              снимок.
            </p>
          </div>
        </div>
      </div>

      <div className="sticky-foot">
        <button className="btn primary" onClick={apply} disabled={!dirty || !!busy}>
          Применить на TV
        </button>
        <button className="btn" onClick={load} disabled={!dirty || !!busy}>
          Отменить изменения
        </button>
        <button className="btn ghost" onClick={() => setConfirmReset(true)} disabled={!!busy || (!loaded.present && !dirty)}>
          Сбросить к встроенному виду
        </button>
        <button className="btn ghost" onClick={screenshot} disabled={!!busy}>
          Снимок экрана с TV
        </button>
        <span className="spacer" />
        {busy ? (
          <Busy text={busy} />
        ) : dirty ? (
            <span className="small">
            <span className="dirty-dot" />
            Ещё не на TV. Применение также сбросит настройки колонок и анимации, сделанные на TV.
          </span>
        ) : (
          <span className="small muted">{loaded.present ? 'На TV уже эти настройки.' : 'TV использует встроенный вид.'}</span>
        )}
      </div>

      {confirmReset && (
        <Confirm title="Сбросить к встроенному виду?" confirmLabel="Сбросить" onConfirm={reset} onCancel={() => setConfirmReset(false)}>
          <p>Файл темы и обои будут удалены с TV, а Home перезапустится со встроенным доком и цветами лаунчера.</p>
          <p className="muted small">Приложения, добавленные или удалённые через библиотеку приложений на TV, останутся как есть.</p>
        </Confirm>
      )}
      {shot && (
        <Modal wide onClose={() => setShot(null)}>
          <h2>TV прямо сейчас</h2>
          <img className="shot" src={shot} alt="Снимок экрана TV" />
          <p className="muted small" style={{ marginTop: 10 }}>
            Чёрный снимок обычно означает, что на экране было Live TV, HDMI-вход или стриминговое приложение. Нажмите
            Home и попробуйте снова.
          </p>
          <div className="modal-actions">
            <button className="btn" onClick={screenshot}>
              Сделать ещё
            </button>
            <button className="btn primary" onClick={() => setShot(null)}>
              Закрыть
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

type SetFn = <K extends keyof LauncherConfig>(key: K, value: LauncherConfig[K] | undefined) => void

function AppsTab({ apps, cfg, set }: { apps: TvApp[]; cfg: LauncherConfig; set: SetFn }): ReactNode {
  const ordered = orderedApps(apps, cfg)
  const hidden = new Set(val(cfg, 'hidden_apps'))
  const columns = val(cfg, 'columns')
  // Non-TV (phone) apps never reach the Home grid — the launcher only lists
  // them in the App library. Surfacing them by name here answers "where did
  // my sideloaded app go" in one glance instead of a bare count (#3).
  const phoneAppList = apps.filter((a) => !a.tv).sort((a, b) => a.label.localeCompare(b.label))
  const phoneApps = phoneAppList.length
  const missingDock = (cfg.dock_apps ?? []).filter((p) => !apps.some((a) => a.pkg === p))

  return (
    <div>
      <p className="muted">
        Перетащите плитки в нужном порядке или используйте стрелки. Первые {columns} видимых приложений образуют док.
        Остальные идут в сетке в этом же порядке.
      </p>
      <div className="dock-legend">
        <span>
          <span className="swatch" />
          Док
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--white)', borderColor: 'var(--line)' }} />
          Сетка
        </span>
        <span className="spacer" />
        <button className="btn small ghost" onClick={() => set('dock_apps', undefined)} disabled={!cfg.dock_apps}>
          Сбросить порядок
        </button>
        <button className="btn small ghost" onClick={() => set('hidden_apps', undefined)} disabled={!cfg.hidden_apps}>
          Показать все
        </button>
      </div>
      <AppOrder
        apps={ordered}
        hidden={hidden}
        columns={columns}
        onOrder={(pkgs) => set('dock_apps', pkgs)}
        onHidden={(pkg, hide) => {
          const next = new Set(hidden)
          if (hide) next.add(pkg)
          else next.delete(pkg)
          set('hidden_apps', [...next])
        }}
      />
      <div className="notice" style={{ marginTop: 16 }}>
        Если вы меняли порядок плиток на самом TV, «Применить» заменит его этим порядком.
        {missingDock.length > 0 && ` ${missingDock.length} приложений из сохранённого порядка не установлены и будут пропущены.`}
      </div>

      {phoneApps > 0 && (
        <div style={{ marginTop: 16 }}>
          <h3>Телефонные приложения на этом TV ({phoneApps})</h3>
          <p className="muted small">
            У них нет ТВ-интерфейса, поэтому лаунчер не показывает их в сетке Home — они доступны только в библиотеке
            приложений (вкладка «Макет»). Здесь ничего чинить не нужно; список просто показывает, что они установлены и
            доступны.
          </p>
          <div className="thumbs" style={{ flexWrap: 'wrap', gap: 6 }}>
            {phoneAppList.map((a) => (
              <span key={a.pkg} className="chip optional" style={{ textTransform: 'none' }}>
                {a.label}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function LayoutTab({ cfg, set }: { cfg: LauncherConfig; set: SetFn }): ReactNode {
  return (
    <div className="stack" style={{ gap: 18 }}>
      <Slider
        label="Приложений в ряду"
        hint="В доке столько же мест. Начиная с 5 плитки уменьшаются, чтобы поместиться."
        value={val(cfg, 'columns')}
        min={4}
        max={8}
        onChange={(v) => set('columns', v)}
      />
      <Slider
        label="Рядов сетки под доком"
        hint="0 прижимает док к нижнему краю экрана."
        value={val(cfg, 'rows_below_dock')}
        min={0}
        max={3}
        onChange={(v) => set('rows_below_dock', v)}
      />
      <div>
        <Toggle label="Док отдельным рядом" hint="Более светлая полоса без названий под плитками. Выкл. — одна общая сетка." checked={val(cfg, 'dock_row')} onChange={(v) => set('dock_row', v)} />
        <Toggle label="Название приложения над доком" hint="Крупное имя приложения в фокусе. Выкл. оставляет только свечение и место для плиток покрупнее." checked={val(cfg, 'dock_title')} onChange={(v) => set('dock_title', v)} />
        <Toggle label="Заполнять пустые места в доке" hint="Если приложений меньше, чем мест, первые приложения из сетки поднимаются вверх. Выкл. оставляет места пустыми." checked={val(cfg, 'fill_dock')} onChange={(v) => set('fill_dock', v)} />
        <Toggle label="Библиотека приложений" hint="Кнопка после последнего ряда показывает все установленные приложения, чтобы добавлять и убирать ярлыки с пульта." checked={val(cfg, 'app_library')} onChange={(v) => set('app_library', v)} />
        <Toggle label="Кнопка изменения порядка в верхней панели" hint="Порядок также доступен в меню долгого нажатия кнопки Settings." checked={val(cfg, 'reorder_button')} onChange={(v) => set('reorder_button', v)} />
        <Toggle label="CPU, память и накопитель под часами" hint="Обновляется только пока Home на экране." checked={val(cfg, 'show_stats')} onChange={(v) => set('show_stats', v)} />
        <Toggle label="Плитка выбора Home" hint="Плитка, открывающая системный выбор главного экрана, если он есть на TV." checked={val(cfg, 'show_home_settings_shortcut')} onChange={(v) => set('show_home_settings_shortcut', v)} />
      </div>
    </div>
  )
}

function BackdropTab(props: {
  cfg: LauncherConfig
  set: SetFn
  setCfg: (fn: (c: LauncherConfig) => LauncherConfig) => void
  wallpaperUrl: string | null
  wall: WallChange
  setWall: (w: WallChange) => void
  rotation: RotationChange
  setRotation: (r: RotationChange) => void
  loadedRotation: string[]
  presets: ThemePreset[]
}): ReactNode {
  const { cfg, set } = props
  const single = useRef<HTMLInputElement>(null)
  const many = useRef<HTMLInputElement>(null)
  const [working, setWorking] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const rotationMinutes = val(cfg, 'wallpaper_rotation_minutes')
  const [tmdbKey, setTmdbKey] = useState('')
  const [tmdbList, setTmdbList] = useState('')

  /** Appends prepared images to the rotation set (issue #5 sources funnel in here). */
  function addToRotation(images: PreparedImage[]): void {
    const out = props.rotation.kind === 'set' ? [...props.rotation.images, ...images] : images
    props.setRotation({ kind: 'set', images: uniqueNames(out).slice(0, 100) })
    set('wallpaper', true)
    if (!rotationMinutes) set('wallpaper_rotation_minutes', 30)
  }

  async function addBing(): Promise<void> {
    setErr(null)
    setWorking('Загружаем картинку дня Bing')
    try {
      addToRotation([await fetchBingDaily()])
    } catch (e) {
      setErr(`Не удалось загрузить картинку Bing: ${errorText(e)}`)
    } finally {
      setWorking(null)
    }
  }

  async function addTmdb(): Promise<void> {
    const key = tmdbKey.trim()
    const list = Number(tmdbList.trim())
    setErr(null)
    if (!key || !Number.isInteger(list) || list <= 0) {
      setErr('Введите ключ TMDB API и номер списка (только цифры, из адреса themoviedb.org/list/…).')
      return
    }
    setWorking('Загружаем кадры фильмов из TMDB')
    try {
      const paths = (await tmdbBackdropPaths(list, key)).slice(0, 20)
      if (!paths.length) {
        setErr('В этом списке TMDB нет подходящих кадров.')
        return
      }
      const images: PreparedImage[] = []
      for (let i = 0; i < paths.length; i++) {
        setWorking(`Загружаем кадр ${i + 1} из ${paths.length}`)
        images.push(await fetchTmdbBackdrop(paths[i]))
      }
      addToRotation(images)
    } catch (e) {
      setErr(`Не удалось использовать TMDB: ${errorText(e)}`)
    } finally {
      setWorking(null)
    }
  }

  async function pickSingle(files: FileList | null): Promise<void> {
    if (!files?.[0]) return
    setErr(null)
    setWorking('Подгоняем картинку под TV')
    try {
      const img = await prepareWallpaper(files[0], 'wallpaper.jpg')
      props.setWall({ kind: 'set', image: img })
      set('wallpaper', true)
    } catch (e) {
      setErr(`Не удалось прочитать картинку: ${errorText(e)}`)
    } finally {
      setWorking(null)
    }
  }

  async function pickMany(files: FileList | null): Promise<void> {
    if (!files?.length) return
    setErr(null)
    const list = [...files].slice(0, 100)
    const out: PreparedImage[] = props.rotation.kind === 'set' ? [...props.rotation.images] : []
    try {
      for (let i = 0; i < list.length; i++) {
        setWorking(`Подгоняем картинку ${i + 1} из ${list.length}`)
        out.push(await prepareWallpaper(list[i]))
      }
      props.setRotation({ kind: 'set', images: uniqueNames(out).slice(0, 100) })
      set('wallpaper', true)
      if (!rotationMinutes) set('wallpaper_rotation_minutes', 30)
    } catch (e) {
      setErr(`Не удалось прочитать одну из картинок: ${errorText(e)}`)
    } finally {
      setWorking(null)
    }
  }

  function applyPreset(p: ThemePreset): void {
    // Presets set the look; the app order and hidden apps you chose stay.
    const { dock_apps: _d, hidden_apps: _h, ...look } = p.config
    props.setCfg((c) => ({ ...c, ...look }))
    if (p.wallpaper) props.setWall({ kind: 'set', image: dataUrlToImage(p.wallpaper, 'wallpaper.jpg') })
  }

  const rotationNames = props.rotation.kind === 'set' ? props.rotation.images.map((i) => i.name) : props.loadedRotation

  return (
    <div className="stack" style={{ gap: 20 }}>
      {props.presets.length > 0 && (
        <div>
          <h3>Начать с готовой темы</h3>
          <div className="presets">
            {props.presets.map((p) => (
              <button key={p.id} className="preset" onClick={() => applyPreset(p)}>
                <div
                  className="preset-thumb"
                  style={{ background: p.wallpaper ? `center / cover url(${p.wallpaper})` : p.config.background_color ?? '#000' }}
                />
                <b>{p.name}</b>
                <span>{p.description}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field">
          Цвет фона
          <span className="hint">Фон для всего. Оставьте тёмным: часы и названия белые.</span>
          <div className="row">
            <input type="color" value={normHex(val(cfg, 'background_color'))} onChange={(e) => set('background_color', e.target.value.toUpperCase())} />
            <input type="text" value={val(cfg, 'background_color')} onChange={(e) => /^#[0-9a-fA-F]{6}$/.test(e.target.value) && set('background_color', e.target.value)} style={{ width: 110 }} />
          </div>
        </label>
      </div>

      <div>
        <h3>Обои</h3>
        <p className="muted small">
          Подойдёт любая картинка. Приложение обрежет её до 1920×1080 и сожмёт до 300 КБ, потому что TV с 1 ГБ памяти
          декодирует её при каждом запуске Home.
        </p>
        <Toggle label="Показывать обои" checked={val(cfg, 'wallpaper')} onChange={(v) => set('wallpaper', v)} />
        <div className="row" style={{ marginTop: 8 }}>
          {props.wallpaperUrl && (
            <div className="thumb">
              <img src={props.wallpaperUrl} alt="Текущие обои" />
              <span>
                {props.wall.kind === 'set' ? `Новые, ${Math.round(props.wall.image.bytes / 1024)} КБ` : 'На TV'}
              </span>
            </div>
          )}
          <input ref={single} type="file" accept="image/*" hidden onChange={(e) => pickSingle(e.target.files).then(() => (e.target.value = ''))} />
          <button className="btn small" onClick={() => single.current?.click()} disabled={!!working}>
            {props.wallpaperUrl ? 'Выбрать другую картинку' : 'Выбрать картинку'}
          </button>
          {props.wallpaperUrl && (
            <button className="btn small ghost" onClick={() => props.setWall({ kind: 'remove' })}>
              Удалить
            </button>
          )}
          {props.wall.kind !== 'keep' && (
            <button className="btn small ghost" onClick={() => props.setWall({ kind: 'keep' })}>
              Оставить картинку с TV
            </button>
          )}
        </div>
        {working && <div style={{ marginTop: 8 }}><Busy text={working} /></div>}
        {err && <div className="notice error" style={{ marginTop: 8 }}>{err}</div>}
      </div>

      <Slider label="Затемнить обои" hint="Чёрный слой поверх картинки, чтобы текст оставался читаемым." value={val(cfg, 'wallpaper_dim')} min={0} max={90} unit="%" onChange={(v) => set('wallpaper_dim', v)} />

      <div>
        <h3>Сменяющиеся обои</h3>
        <p className="muted small">
          Добавьте несколько картинок — лаунчер будет случайно выбирать новую каждые несколько минут, только пока Home на
          экране. Они заменят набор, уже сохранённый на TV.
        </p>
        {rotationNames.length > 0 && (
          <div className="thumbs" style={{ marginBottom: 10 }}>
            {props.rotation.kind === 'set'
              ? props.rotation.images.map((img, i) => (
                  <div key={img.name} className="thumb">
                    <img src={img.dataUrl} alt={img.name} />
                    <span>{img.name}</span>
                    <button
                      className="btn small"
                      style={{ padding: '0 6px' }}
                      aria-label={`Удалить ${img.name}`}
                      onClick={() => props.rotation.kind === 'set' && props.setRotation({ kind: 'set', images: props.rotation.images.filter((_, j) => j !== i) })}
                    >
                      ✕
                    </button>
                  </div>
                ))
              : rotationNames.map((n) => (
                  <span key={n} className="chip optional" style={{ textTransform: 'none' }}>
                    {n}
                  </span>
                ))}
          </div>
        )}
        <div className="row">
          <input ref={many} type="file" accept="image/*" multiple hidden onChange={(e) => pickMany(e.target.files).then(() => (e.target.value = ''))} />
          <button className="btn small" onClick={() => many.current?.click()} disabled={!!working}>
            Добавить картинки
          </button>
          {rotationNames.length > 0 && (
            <button className="btn small ghost" onClick={() => props.setRotation({ kind: 'set', images: [] })}>
              Удалить все
            </button>
          )}
        </div>
        <label className="field" style={{ marginTop: 12, maxWidth: 260 }}>
          Менять картинку каждые
          <select value={rotationMinutes} onChange={(e) => set('wallpaper_rotation_minutes', Number(e.target.value))}>
            {[...new Set([0, 5, 15, 30, 60, 120, 360, 720, 1440, rotationMinutes])]
              .sort((a, b) => a - b)
              .map((m) => (
                <option key={m} value={m}>
                  {m === 0 ? 'Никогда (одни обои)' : m < 60 ? `${m} минут` : m === 60 ? '1 час' : `${m / 60} часов`}
                </option>
              ))}
          </select>
        </label>

        <div style={{ marginTop: 16 }}>
          <h4 style={{ marginBottom: 4 }}>Получить картинки</h4>
          <p className="muted small">
            Загружаются на этом компьютере, подгоняются под TV и добавляются в набор выше. На TV ничего не загружается, и
            ключи на него не отправляются.
          </p>
          <div className="row" style={{ marginTop: 8, flexWrap: 'wrap' }}>
            <button className="btn small" onClick={() => addBing()} disabled={!!working}>
              Картинка дня Bing
            </button>
            <button className="btn small" onClick={() => addTmdb()} disabled={!!working}>
              Кадры TMDB
            </button>
          </div>
          <div className="row" style={{ marginTop: 8, flexWrap: 'wrap' }}>
            <input
              type="password"
              value={tmdbKey}
              onChange={(e) => setTmdbKey(e.target.value)}
              placeholder="Ключ TMDB API (v3)"
              style={{ width: 220 }}
              autoComplete="off"
            />
            <input
              type="text"
              value={tmdbList}
              onChange={(e) => setTmdbList(e.target.value.replace(/[^0-9]/g, ''))}
              placeholder="Номер списка, напр. 28"
              style={{ width: 150 }}
              inputMode="numeric"
            />
          </div>
          <p className="muted small" style={{ marginTop: 6 }}>
            Ключ остаётся в этом окне и никогда не сохраняется и не отправляется на TV. Бесплатный ключ можно создать на
            themoviedb.org; номер списка — цифры в конце адреса themoviedb.org/list/….
          </p>
        </div>
      </div>

      <Slider
        label="Небо по времени суток"
        hint="Тёмный градиент, следующий за часами: ночная синева, мягкий голубой день, тёплый вечер. Около 60 хорошо смотрится поверх обоев. 0 отключает."
        value={val(cfg, 'sky_intensity')}
        min={0}
        max={100}
        onChange={(v) => set('sky_intensity', v)}
      />
    </div>
  )
}

function GreetingTab({ cfg, set }: { cfg: LauncherConfig; set: SetFn }): ReactNode {
  const festival = cfg.festival ?? ''
  return (
    <div className="stack" style={{ gap: 18 }}>
      <label className="field">
        Имя после приветствия
        <span className="hint">«Мама» покажет ДОБРОЕ УТРО, МАМА. Оставьте пустым, чтобы было только приветствие.</span>
        <input type="text" value={val(cfg, 'greeting_name')} onChange={(e) => set('greeting_name', e.target.value || undefined)} placeholder="Мама" />
      </label>
      <Toggle label="Иранская дата (Shamsi)" hint="Показывает дату по календарю Solar Hijri, принятому в Иране и Афганистане, над обычной датой." checked={val(cfg, 'shamsi_date')} onChange={(v) => set('shamsi_date', v)} />

      <div className="card" style={{ marginBottom: 0 }}>
        <h3>Праздничное оформление</h3>
        <p className="muted small">
          Значок сети станет дийей, «Входы» — храмовым колоколом, «Настройки» — бархатцем, а первый запуск Home за день
          встретит лепестками. После последнего дня оформление отключится само.
        </p>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field">
            Праздник
            <select
              value={festival}
              onChange={(e) => {
                const v = e.target.value
                if (!v) {
                  set('festival', undefined)
                  set('festival_until', undefined)
                  set('greeting', undefined)
                  set('accent_color', undefined)
                } else set('festival', v)
              }}
            >
              <option value="">Нет</option>
              <option value="diwali">Дивали</option>
              <option value="dussehra">Дуссехра</option>
            </select>
          </label>
          {festival && (
            <>
              <label className="field">
                Последний день
                <input type="date" value={cfg.festival_until ?? ''} onChange={(e) => set('festival_until', e.target.value || undefined)} />
              </label>
              <label className="field">
                Цвет свечения
                <input type="color" value={normHex(cfg.accent_color ?? '#FFB23C')} onChange={(e) => set('accent_color', e.target.value.toUpperCase())} />
              </label>
            </>
          )}
        </div>
        {festival && (
          <label className="field" style={{ marginTop: 12 }}>
            Приветствие на время праздника
            <span className="hint">Оставьте пустым для встроенного.</span>
            <input type="text" value={cfg.greeting ?? ''} onChange={(e) => set('greeting', e.target.value || undefined)} placeholder="С Дивали!" />
          </label>
        )}
      </div>
    </div>
  )
}

function MotionTab({ cfg, set }: { cfg: LauncherConfig; set: SetFn }): ReactNode {
  return (
    <div>
      <Toggle label="Анимации" hint="Фокус, стеклянный блик, появление заголовка и плиток." checked={val(cfg, 'animations')} onChange={(v) => set('animations', v)} />
      <Toggle label="Продвинутые эффекты" hint="Подъём плитки, увеличение иконки, плавная смена цвета свечения и эффекты открытия приложений. Больше памяти." checked={val(cfg, 'advanced_effects')} onChange={(v) => set('advanced_effects', v)} />
      <Toggle
        label="Уменьшенная анимация"
        hint="Для TV, графика которых не успевает: резкая подсветка, без перелётов, короткие затухания. Известный случай — MediaTek MT5895 на Android 10."
        checked={val(cfg, 'reduced_motion')}
        onChange={(v) => set('reduced_motion', v)}
      />
      <p className="muted small" style={{ marginTop: 10 }}>
        Скорость системных анимаций — в разделе «Дополнительно».
      </p>
    </div>
  )
}

function AdvancedTab({ apps, cfg, set }: { apps: TvApp[]; cfg: LauncherConfig; set: SetFn }): ReactNode {
  const sorted = useMemo(() => [...apps].sort((a, b) => a.label.localeCompare(b.label)), [apps])
  const keys = cfg.app_keys ?? []
  const rows = keys.map((k) => {
    const i = k.indexOf(':')
    return { code: k.slice(0, i), pkg: k.slice(i + 1) }
  })
  const writeKeys = (r: { code: string; pkg: string }[]): void =>
    set('app_keys', r.length ? r.map((x) => `${x.code}:${x.pkg}`) : undefined)

  return (
    <div className="stack" style={{ gap: 18 }}>
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field" style={{ flex: 1 }}>
          Приложение Live TV
          <span className="hint">Показывается в конце меню «Входы».</span>
          <AppSelect apps={sorted} value={val(cfg, 'live_tv_package')} onChange={(v) => set('live_tv_package', v)} allowCustom />
        </label>
        <label className="field" style={{ flex: 1 }}>
          VPN-приложение
          <span className="hint">Его открывает значок сети; пока VPN не подключён, показывается метка «VPN выкл.».</span>
          <AppSelect apps={sorted} value={val(cfg, 'vpn_app')} onChange={(v) => set('vpn_app', v)} />
        </label>
      </div>

      <div>
        <h3>Кнопки пульта, открывающие приложение</h3>
        <p className="muted small">
          Для кнопок, на которые реагировал только штатный лаунчер, например кнопка YouTube на Google TV (190). Нужна
          служба кнопок Input или Home из шага «Лаунчер».
        </p>
        {rows.map((r, i) => (
          <div key={i} className="kv-row">
            <input
              type="text"
              value={r.code}
              placeholder="190"
              aria-label="Код кнопки"
              onChange={(e) => writeKeys(rows.map((x, j) => (j === i ? { ...x, code: e.target.value.trim() } : x)))}
            />
            <AppSelect apps={sorted} value={r.pkg} onChange={(v) => writeKeys(rows.map((x, j) => (j === i ? { ...x, pkg: v } : x)))} />
            <button className="icon-btn" aria-label="Удалить" onClick={() => writeKeys(rows.filter((_, j) => j !== i))}>
              ✕
            </button>
          </div>
        ))}
        <div className="row">
          <button className="btn small" onClick={() => writeKeys([...rows, { code: '190', pkg: 'com.google.android.youtube.tv' }])}>
            Добавить кнопку
          </button>
          <span className="small muted">Код кнопки числом или именем, например KEYCODE_BUTTON_3.</span>
        </div>
      </div>

      <NumberList
        label="Кнопки, открывающие Настройки"
        hint="Коды для службы кнопки Home. Кнопка-шестерёнка на TCL Google TV: 83."
        value={cfg.settings_key_keycodes}
        onChange={(v) => set('settings_key_keycodes', v)}
      />
      <Toggle
        label="Кнопка Settings открывает быструю панель"
        hint="Небольшая панель с настройками TV, экрана, сети, приложений и лаунчера поверх текущего экрана."
        checked={val(cfg, 'settings_key_panel')}
        onChange={(v) => set('settings_key_panel', v)}
      />
      <NumberList
        label="Дополнительные кнопки для переключателя входов"
        hint="Коды для службы кнопки Input. KEYCODE_TV работает всегда. Кнопка Xiaomi PatchWall: 192."
        value={cfg.input_key_keycodes}
        onChange={(v) => set('input_key_keycodes', v)}
      />
      <Toggle
        label="Карточки модулей"
        hint="Приложения, предлагающие свою плитку, спрашивают по одному разу. Выкл.: лаунчер не ищет и не спрашивает."
        checked={val(cfg, 'module_cards')}
        onChange={(v) => set('module_cards', v)}
      />
    </div>
  )
}

function AppSelect(props: { apps: TvApp[]; value: string; onChange: (v: string) => void; allowCustom?: boolean }): ReactNode {
  const known = props.apps.some((a) => a.pkg === props.value)
  return (
    <select value={props.value} onChange={(e) => props.onChange(e.target.value)}>
      <option value="">Нет</option>
      {!known && props.value && <option value={props.value}>{props.value} (не установлено)</option>}
      {props.apps.map((a) => (
        <option key={a.pkg} value={a.pkg}>
          {a.label}
        </option>
      ))}
    </select>
  )
}

function NumberList(props: { label: string; hint: string; value?: number[]; onChange: (v: number[] | undefined) => void }): ReactNode {
  const [text, setText] = useState((props.value ?? []).join(', '))
  useEffect(() => setText((props.value ?? []).join(', ')), [props.value?.join(',')])
  return (
    <label className="field">
      {props.label}
      <span className="hint">{props.hint}</span>
      <input
        type="text"
        value={text}
        placeholder="83, 190"
        onChange={(e) => setText(e.target.value)}
        onBlur={() => {
          const nums = text.split(/[\s,]+/).filter(Boolean).map(Number).filter((n) => Number.isInteger(n) && n > 0)
          props.onChange(nums.length ? nums : undefined)
          setText(nums.join(', '))
        }}
      />
    </label>
  )
}

function normHex(c: string): string {
  return /^#[0-9a-fA-F]{6}$/.test(c) ? c.toLowerCase() : BUILTIN.background_color
}

function sortKeys(o: LauncherConfig): Record<string, unknown> {
  return Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined).sort(([a], [b]) => a.localeCompare(b)))
}
