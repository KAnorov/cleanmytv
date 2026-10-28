import { useEffect, useState, type ReactNode } from 'react'
import type { ApplyResult, CheckItem, DownloadProgress, InstallRequest, LauncherStatus, ToolId } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, Confirm, ResultNotice, StatusChip, Toggle, errorText } from '../components/ui'

export function Launcher({ go, markDone }: ScreenProps): ReactNode {
  const [st, setSt] = useState<LauncherStatus | null>(null)
  const [apk, setApk] = useState<'bundled' | 'local'>('bundled')
  const [stock, setStock] = useState<'auto' | 'none'>('auto')
  const [inputKey, setInputKey] = useState(false)
  const [homeKey, setHomeKey] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [askReplace, setAskReplace] = useState(false)
  const [askStock, setAskStock] = useState(false)
  const [advanced, setAdvanced] = useState(false)

  const refresh = (): void => {
    window.cytv.launcherStatus().then((s) => {
      setSt(s)
      if (!s.bundledApk && s.localApk) setApk('local')
    })
  }
  useEffect(refresh, [])

  // Google TV builds use launcherx; its stock remote's Input button needs the service once launcherx is off.
  useEffect(() => {
    if (st?.stockLauncher === 'com.google.android.apps.tv.launcherx') setInputKey(true)
  }, [st?.stockLauncher])

  async function install(replaceIncompatible = false): Promise<void> {
    setAskReplace(false)
    setBusy('Устанавливаем лаунчер. Не выключайте TV.')
    setResult(null)
    const req: InstallRequest = { apk, stockLauncher: stock, inputKeyService: inputKey, homeKeyService: homeKey, replaceIncompatible }
    try {
      const r = await window.cytv.install(req)
      setResult(r)
      if (r.incompatible) setAskReplace(true)
      if (r.ok) markDone('launcher')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      refresh()
    }
  }

  async function useStock(): Promise<void> {
    setAskStock(false)
    setBusy('Возвращаем Home штатному лаунчеру')
    try {
      setResult(await window.cytv.useStockLauncher())
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      refresh()
    }
  }

  const canInstall = st && (apk === 'bundled' ? st.bundledApk : st.localApk)

  return (
    <>
      <h1>Лаунчер</h1>
      <p className="lede">
        Новый главный экран — приложение на 390 КБ: ваши приложения в доке и сетке, часы и никакой рекламы. Приложение
        устанавливает его, запускает и только потом отключает штатный лаунчер. Если Home не переключится на новый, старый
        вернётся на место.
      </p>

      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {busy && (
        <div className="notice">
          <Busy text={busy} />
        </div>
      )}

      {!st ? (
        <Busy text="Спрашиваем TV, что владеет Home" />
      ) : (
        <>
          <div className="card">
            <div className="card-head">
              <h2>Сейчас на этом TV</h2>
              <StatusChip status={st.isHome ? 'ok' : st.installed ? 'warn' : 'optional'} text={st.isHome ? 'Это Home' : st.installed ? 'Установлен, не Home' : 'Не установлен'} />
            </div>
            <div className="facts">
              <div>
                <span>Главный экран</span>
                <b className="mono">{st.homeComponent ?? 'ничего не отвечает'}</b>
              </div>
              <div>
                <span>Штатный лаунчер</span>
                <b className="mono">{st.stockLauncher ?? 'не найден'}</b>
              </div>
              <div>
                <span>Лаунчер CleanYourTV</span>
                <b>{st.installed ? `версия ${st.versionName ?? '?'}` : 'не установлен'}</b>
              </div>
              <div>
                <span>В этом приложении</span>
                <b>{st.bundledApk ? `версия ${st.bundledVersion ?? '?'}` : 'нет встроенной копии'}</b>
              </div>
            </div>
          </div>

          <div className="card white">
            <h2>{st.installed ? 'Переустановить или обновить' : 'Установить'}</h2>

            {st.localApk && (
              <div className="seg" style={{ marginBottom: 14 }}>
                <button className={apk === 'bundled' ? 'on' : ''} onClick={() => setApk('bundled')} disabled={!st.bundledApk}>
                  Встроенная копия
                </button>
                <button className={apk === 'local' ? 'on' : ''} onClick={() => setApk('local')}>
                  Собрано на этом компьютере
                </button>
              </div>
            )}

            <Toggle
              label="Отключить штатный лаунчер"
              hint={
                st.stockLauncher
                  ? `Отключит ${st.stockLauncher}, когда новый лаунчер запустится. Без этого TV может спрашивать, какой Home использовать.`
                  : 'Приложение спросит TV, какой лаунчер владеет Home, и отключит только тот, который знает.'
              }
              checked={stock === 'auto'}
              onChange={(v) => setStock(v ? 'auto' : 'none')}
            />
            <Toggle
              label="Кнопка Input работает из любого приложения"
              hint="Нужно на Google TV после отключения штатного лаунчера. Также включает настраиваемые кнопки пульта (Настройка → Дополнительно)."
              checked={inputKey}
              onChange={setInputKey}
            />
            <Toggle
              label="Исправить кнопку Home"
              hint="Для пультов, у которых Home открывает «Все приложения», например у Xiaomi. Также перенаправляет кнопку Settings."
              checked={homeKey}
              onChange={setHomeKey}
            />
            {(inputKey || homeKey) && (
              <p className="small muted" style={{ marginTop: 6 }}>
                Это службы доступности. Android отключает их при каждой переустановке, поэтому приложение включает их снова
                каждый раз.
              </p>
            )}

            <div className="row" style={{ marginTop: 16 }}>
              <button className="btn primary" disabled={!canInstall || !!busy} onClick={() => install()}>
                {st.installed ? 'Переустановить лаунчер' : 'Установить лаунчер'}
              </button>
              {st.isHome && (
                <button className="btn" disabled={!!busy} onClick={() => setAskStock(true)}>
                  Вернуть Home штатному лаунчеру
                </button>
              )}
              <span className="spacer" />
              {st.isHome && (
                <button className="btn ghost" onClick={() => go('customise')}>
                  Далее: настройка
                </button>
              )}
            </div>
            {!st.bundledApk && !st.localApk && (
              <div className="notice warn" style={{ marginTop: 14, marginBottom: 0 }}>
                В этой копии приложения нет встроенного лаунчера. Соберите его из исходников ниже.
              </div>
            )}
          </div>

          <div>
            <button className="btn small ghost" onClick={() => setAdvanced(!advanced)} aria-expanded={advanced}>
              {advanced ? '▾' : '▸'} Дополнительно: сборка из исходников
            </button>
            {advanced && <BuildFromSource onBuilt={refresh} />}
          </div>
        </>
      )}

      {askReplace && (
        <Confirm title="Заменить лаунчер на TV?" confirmLabel="Заменить" onConfirm={() => install(true)} onCancel={() => setAskReplace(false)}>
          <p>Лаунчер на TV собран на другом компьютере, поэтому Android не сможет обновить его на месте.</p>
          <p>
            Приложение может удалить его и установить этот. Порядок плиток и выбранные приложения на TV будут потеряны.
            Настройки, отправленные из этого приложения, сохранятся.
          </p>
        </Confirm>
      )}
      {askStock && (
        <Confirm title="Снова использовать штатный лаунчер?" confirmLabel="Вернуть" onConfirm={useStock} onCancel={() => setAskStock(false)}>
          <p>
            Приложение включит {st?.stockLauncher ?? 'штатный лаунчер'} и сделает его Home. Лаунчер CleanYourTV останется
            установленным, чтобы можно было переключиться обратно позже.
          </p>
        </Confirm>
      )}
    </>
  )
}

const BUILD_TOOLS: ToolId[] = ['jdk', 'sdk', 'source']

function BuildFromSource({ onBuilt }: { onBuilt: () => void }): ReactNode {
  const [checks, setChecks] = useState<CheckItem[] | null>(null)
  const [progress, setProgress] = useState<Partial<Record<ToolId, DownloadProgress>>>({})
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)

  const refresh = (): void => {
    window.cytv.checks().then((c) => setChecks(c.filter((x) => BUILD_TOOLS.includes(x.id as ToolId))))
  }
  useEffect(() => {
    refresh()
    return window.cytv.onProgress((p) => setProgress((prev) => ({ ...prev, [p.tool]: p })))
  }, [])

  async function getAll(): Promise<void> {
    for (const c of checks ?? []) {
      if (c.status === 'ok' || !c.download) continue
      setBusy(`Загрузка ${c.label}`)
      const r = await window.cytv.download(c.download).catch((e) => ({ ok: false, summary: errorText(e), warnings: [] }))
      if (!r.ok) {
        setResult(r)
        break
      }
    }
    setBusy(null)
    refresh()
  }

  async function build(): Promise<void> {
    setBusy('Собираем лаунчер. Первая сборка скачает Gradle и займёт несколько минут.')
    setResult(null)
    try {
      setResult(await window.cytv.buildLocal())
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      onBuilt()
    }
  }

  const ready = checks?.every((c) => c.status === 'ok')
  return (
    <div className="card" style={{ marginTop: 8 }}>
      <p className="muted">
        Соберите лаунчер на этом компьютере из исходников cleanyourtv. Приложение скачает Java 17, Android SDK и
        исходники в свою папку — около 400 МБ. В систему ничего не устанавливается.
      </p>
      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {!checks ? (
        <Busy text="Проверка" />
      ) : (
        <ul className="checklist" style={{ marginBottom: 14 }}>
          {checks.map((c) => {
            const p = c.download ? progress[c.download] : undefined
            return (
              <li key={c.id} className="check">
                <div className="check-title">
                  <StatusChip status={c.status === 'optional' ? 'missing' : c.status} text={c.status === 'ok' ? 'Готово' : 'Ещё нет'} />
                  <span>{c.label}</span>
                </div>
                <div className="check-detail">
                  {c.detail}
                  {p && p.phase !== 'done' && busy && (
                    <div className="progress">
                      <div style={{ width: `${p.total ? (p.received / p.total) * 100 : 0}%` }} />
                    </div>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
      {busy && <Busy text={busy} />}
      <div className="row" style={{ marginTop: 10 }}>
        {!ready && (
          <button className="btn" onClick={getAll} disabled={!!busy || !checks}>
            Скачать недостающее
          </button>
        )}
        <button className="btn" onClick={build} disabled={!ready || !!busy}>
          Собрать лаунчер
        </button>
      </div>
    </div>
  )
}
