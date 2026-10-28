import { useCallback, useEffect, useState, type ReactNode } from 'react'
import type { ApplyResult, CheckItem, DownloadProgress, ScanHit, ToolId } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, ResultNotice, StatusChip, errorText } from '../components/ui'

const IP_RE = /^\d{1,3}(\.\d{1,3}){3}(:\d{2,5})?$/

export function Setup({ status, go }: ScreenProps): ReactNode {
  const [checks, setChecks] = useState<CheckItem[] | null>(null)
  const [progress, setProgress] = useState<Partial<Record<ToolId, DownloadProgress>>>({})
  const [downloading, setDownloading] = useState<Set<ToolId>>(new Set())
  const [downloadResult, setDownloadResult] = useState<ApplyResult | null>(null)

  const [hits, setHits] = useState<ScanHit[] | null>(null)
  const [scanning, setScanning] = useState(false)
  const [host, setHost] = useState('')
  const [connectError, setConnectError] = useState<string | null>(null)

  const refreshChecks = useCallback(() => {
    window.cytv.checks().then(setChecks, () => setChecks([]))
  }, [])

  useEffect(() => {
    refreshChecks()
    window.cytv.lastHost().then((h) => h && setHost((cur) => cur || h))
    return window.cytv.onProgress((p) => setProgress((prev) => ({ ...prev, [p.tool]: p })))
  }, [refreshChecks])

  // The checklist depends on the connection.
  useEffect(() => {
    if (status.state === 'connected' || status.state === 'error' || status.state === 'idle') refreshChecks()
  }, [status.state, refreshChecks])

  async function download(tool: ToolId): Promise<void> {
    setDownloading((s) => new Set(s).add(tool))
    try {
      setDownloadResult(await window.cytv.download(tool))
    } catch (e) {
      setDownloadResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setDownloading((s) => {
        const n = new Set(s)
        n.delete(tool)
        return n
      })
      refreshChecks()
    }
  }

  async function scan(): Promise<void> {
    setScanning(true)
    try {
      const found = await window.cytv.scan()
      setHits(found)
      if (found.length === 1 && !host) setHost(found[0].host)
    } catch (e) {
      setConnectError(errorText(e))
    } finally {
      setScanning(false)
    }
  }

  // The saved TV wasn't at its address: look for it, and let the user pick (the app never logs in to
  // scanned devices by itself).
  useEffect(() => {
    if (status.moved && hits === null && !scanning) scan()
  }, [status.moved])

  async function connect(h = host): Promise<void> {
    setConnectError(null)
    try {
      const s = await window.cytv.connect(h.trim())
      if (s.state === 'error' && !s.needsPairing) setConnectError(s.error ?? 'Не удалось подключиться.')
    } catch (e) {
      setConnectError(errorText(e))
    }
  }

  const busy = status.state === 'connecting' || status.state === 'waiting-auth'
  const connected = status.state === 'connected'
  const required = checks?.filter((c) => !['jdk', 'sdk', 'source', 'platform-tools'].includes(c.id)) ?? []
  const optional = checks?.filter((c) => ['jdk', 'sdk', 'source', 'platform-tools'].includes(c.id)) ?? []

  return (
    <>
      <h1>Подключение</h1>
      <p className="lede">
        Приложение связывается с TV по вашему Wi-Fi. Для очистки TV и установки лаунчера не нужны Python, Java или
        Android-инструменты. Необязательные компоненты нужны только для самостоятельной сборки лаунчера или сопряжения
        некоторых Google TV.
      </p>

      {status.state === 'waiting-auth' && <WaitingForAllow />}

      {connected && status.probe ? (
        <div className="card white">
          <div className="tv-card">
            <div className="tv-glyph" aria-hidden />
            <div>
              <div className="row between">
                <h2 style={{ margin: 0 }}>
                  {status.probe.manufacturer} {status.probe.model}
                </h2>
                <div className="row">
                  <StatusChip status="ok" text="Подключено" />
                  <button className="btn small ghost" onClick={() => window.cytv.disconnect()}>
                    Отключить
                  </button>
                </div>
              </div>
              <div className="facts">
                <div>
                  <span>Адрес</span>
                  <b>{status.host}</b>
                </div>
                <div>
                  <span>Android</span>
                  <b>
                    {status.probe.release} (API {status.probe.sdk})
                  </b>
                </div>
                <div>
                  <span>Устройство</span>
                  <b>{status.probe.device}</b>
                </div>
                <div>
                  <span>Сборка</span>
                  <b className="mono">{status.probe.build}</b>
                </div>
              </div>
            </div>
          </div>
          {status.probe.adbSecure === '0' && (
            <div className="notice warn" style={{ marginTop: 16, marginBottom: 0 }}>
              Этот TV сообщает <code>ro.adb.secure=0</code>. Любое устройство в вашей сети может подключиться к порту
              отладки без запроса разрешения. Закройте его после работы: шаг «Завершение» подскажет как.
            </div>
          )}
          {status.probe.sdk > 0 && status.probe.sdk < 19 && (
            <div className="notice warn" style={{ marginTop: 16, marginBottom: 0 }}>
              Этот TV работает на Android {status.probe.release} (API {status.probe.sdk}). Встроенному лаунчеру нужен
              Android 4.4 (API 19) или новее, поэтому шаг «Лаунчер», скорее всего, откажется устанавливаться. Очистка
              остаётся доступной и обратимой (ничего не удаляется), но сначала проверьте таблицу известных устройств и
              пропустите шаг «Лаунчер» для моделей, отмеченных как неподдерживаемые.
            </div>
          )}
          <div className="row" style={{ marginTop: 16 }}>
            <button className="btn primary" onClick={() => go('debloat')}>
              Далее: очистка TV
            </button>
          </div>
        </div>
      ) : (
        <div className="grid-2">
          <div className="card">
            <h2>1. На телевизоре</h2>
            <ol className="guide">
              <li>
                <span>
                  Откройте <span className="path">Настройки<i>→</i>Настройки устройства<i>→</i>О телевизоре</span>. На
                  некоторых Google TV это <span className="path">Настройки<i>→</i>Система<i>→</i>О телевизоре</span>.
                </span>
              </li>
              <li>
                <span>
                  Прокрутите до пункта <b>Сборка</b> (или <b>Сборка Android TV OS</b>) и нажмите на нём OK семь раз. TV
                  сообщит, что вы стали разработчиком.
                </span>
              </li>
              <li>
                <span>Вернитесь на экран назад и откройте <b>Для разработчиков</b>.</span>
              </li>
              <li>
                <span>
                  Включите <b>Отладку по USB</b>. Если есть также <b>Отладка по сети</b> или <b>Отладка ADB</b>,
                  включите и её.
                </span>
              </li>
              <li>
                <span>Оставьте TV включённым и подключённым к той же Wi-Fi сети, что и компьютер.</span>
              </li>
            </ol>
          </div>

          <div className="card">
            <h2>2. Найдите свой TV</h2>
            <p className="muted small">Сканирование ищет TV с открытым портом 5555 в вашей сети. Это занимает несколько секунд.</p>
            {status.moved && status.error && (
              <div className="notice warn" style={{ marginBottom: 12 }}>
                {status.error} Подключайтесь только к адресу, который вы узнаёте как свой TV.
              </div>
            )}
            <div className="row" style={{ marginBottom: 12 }}>
              <button className="btn" onClick={scan} disabled={scanning || busy}>
                {scanning ? 'Сканирование…' : hits ? 'Сканировать снова' : 'Сканировать сеть'}
              </button>
              {scanning && <span className="spinner" />}
            </div>
            {hits && hits.length === 0 && (
              <div className="notice warn">
                Ни один TV не ответил. Проверьте, что отладка по USB включена, а TV подключён к этой Wi-Fi сети, или
                введите адрес ниже (Настройки TV → Сеть → ваша сеть).
              </div>
            )}
            {hits && hits.length > 0 && (
              <ul className="host-list">
                {hits.map((h) => (
                  <li key={h.host}>
                    <button
                      className={`host-item${host === h.host ? ' selected' : ''}`}
                      onClick={() => setHost(h.host)}
                      onDoubleClick={() => connect(h.host)}
                    >
                      <span className="dot ok" />
                      <span style={{ flex: 1 }}>{h.host}</span>
                      <span className="muted small">порт 5555 открыт</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <form
              className="stack"
              onSubmit={(e) => {
                e.preventDefault()
                connect()
              }}
            >
              <label className="field">
                Адрес TV
                <input type="text" value={host} onChange={(e) => setHost(e.target.value)} placeholder="192.168.1.50" />
              </label>
              <div className="row">
                <button className="btn primary" type="submit" disabled={busy || !IP_RE.test(host.trim())}>
                  {status.state === 'connecting' ? 'Подключение…' : 'Подключить'}
                </button>
                {status.state === 'connecting' && <Busy text="Открываем соединение" />}
              </div>
            </form>
            {connectError && (
              <div className="notice error" style={{ marginTop: 14, marginBottom: 0 }}>
                {connectError}
              </div>
            )}
          </div>
        </div>
      )}

      {status.needsPairing && !connected && <PairForm host={status.host ?? host} onPaired={() => connect(status.host ?? host)} />}

      <div className="card white" style={{ marginTop: 8 }}>
        <div className="card-head">
          <h2>Что готово</h2>
          <button className="btn small ghost" onClick={refreshChecks}>
            Проверить снова
          </button>
        </div>
        <ResultNotice result={downloadResult} onDismiss={() => setDownloadResult(null)} />
        {!checks ? (
          <Busy text="Проверка компьютера" />
        ) : (
          <>
            <Checklist items={required} progress={progress} downloading={downloading} onDownload={download} />
            {optional.length > 0 && (
              <>
                <h3 style={{ marginTop: 22 }}>По желанию</h3>
                <p className="muted small">
                  Нужно только для сборки лаунчера из исходников (шаг «Лаунчер», «Дополнительно») или для сопряжения TV,
                  который запрашивает код. Всё скачивается в папку этого приложения.
                </p>
                <Checklist items={optional} progress={progress} downloading={downloading} onDownload={download} />
              </>
            )}
          </>
        )}
      </div>
    </>
  )
}

function Checklist(props: {
  items: CheckItem[]
  progress: Partial<Record<ToolId, DownloadProgress>>
  downloading: Set<ToolId>
  onDownload: (t: ToolId) => void
}): ReactNode {
  return (
    <ul className="checklist">
      {props.items.map((c) => {
        const p = c.download ? props.progress[c.download] : undefined
        const active = c.download ? props.downloading.has(c.download) : false
        return (
          <li key={c.id} className="check">
            <div className="check-title">
              <StatusChip status={c.status} />
              <span>{c.label}</span>
            </div>
            <div className="check-detail">
              {c.detail}
              {active && p && <DownloadBar p={p} />}
              {active && !p && <div className="progress indeterminate"><div /></div>}
            </div>
            <div className="check-actions">
              {c.download && (
                <button className="btn small" onClick={() => props.onDownload(c.download!)} disabled={active}>
                  {active ? 'Загрузка…' : 'Скачать'}
                </button>
              )}
            </div>
          </li>
        )
      })}
    </ul>
  )
}

function DownloadBar({ p }: { p: DownloadProgress }): ReactNode {
  const pct = p.total > 0 ? Math.min(100, (p.received / p.total) * 100) : 0
  const label =
    p.phase === 'download'
      ? `${mb(p.received)} из ${p.total ? mb(p.total) : '?'}`
      : p.phase === 'extract'
        ? 'Распаковка'
        : p.phase === 'install'
          ? p.message ?? 'Установка'
          : p.phase === 'error'
            ? p.message ?? 'Ошибка'
            : 'Готово'
  return (
    <div>
      <div className={`progress${p.phase === 'download' && p.total ? '' : ' indeterminate'}`}>
        <div style={p.phase === 'download' && p.total ? { width: `${pct}%` } : undefined} />
      </div>
      <span className="small muted">{label}</span>
    </div>
  )
}

const mb = (n: number): string => `${(n / 1e6).toFixed(n > 1e8 ? 0 : 1)} MB`

function WaitingForAllow(): ReactNode {
  return (
    <div className="waiting" role="status">
      <div className="allow-mock" aria-hidden>
        <b>Разрешить отладку по USB?</b>
        <div className="small">Отпечаток ключа RSA…</div>
        <div className="small">☑ Всегда разрешать с этого компьютера</div>
        <div className="am-btn">Отмена</div>
        <div className="am-btn focus">Разрешить</div>
      </div>
      <div>
        <div className="big">Посмотрите на TV и выберите «Разрешить».</div>
        <p className="muted" style={{ marginTop: 6 }}>
          Сначала поставьте галочку <b>Всегда разрешать с этого компьютера</b>, чтобы запрос не появлялся снова. Если
          ничего не видно, нажмите Home на пульте: запрос может скрываться за запущенным приложением.
        </p>
        <Busy text="Ожидание TV" />
      </div>
    </div>
  )
}

function PairForm({ host, onPaired }: { host: string; onPaired: () => void }): ReactNode {
  const [pairPort, setPairPort] = useState('')
  const [code, setCode] = useState('')
  const [connectPort, setConnectPort] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<ApplyResult | null>(null)

  async function pair(): Promise<void> {
    setBusy(true)
    setResult(null)
    try {
      const r = await window.cytv.pair(host, Number(pairPort), code.trim(), Number(connectPort))
      setResult(r)
      if (r.ok) onPaired()
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(false)
    }
  }

  const valid = /^\d{4,5}$/.test(pairPort) && /^\d{6}$/.test(code.trim()) && /^\d{4,5}$/.test(connectPort)
  return (
    <div className="card white">
      <h2>Этому TV нужен код сопряжения</h2>
      <p className="muted">
        Некоторые Google TV (Sony, отдельные Philips) держат порт 5555 закрытым, пока вы не выполните сопряжение.
        Используется Google adb — приложение скачает его, если его ещё нет.
      </p>
      <ol className="guide" style={{ marginBottom: 14 }}>
        <li>
          <span>
            На TV: <span className="path">Для разработчиков<i>→</i>Отладка по Wi-Fi</span>, включите её и запишите{' '}
            <b>IP-адрес и порт</b> с этого экрана. Это порт подключения.
          </span>
        </li>
        <li>
          <span>
            Выберите <b>Сопряжение устройства с помощью кода</b>. Запишите 6-значный код и порт под ним. Это порт
            сопряжения.
          </span>
        </li>
      </ol>
      <ResultNotice result={result} />
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field" style={{ width: 120 }}>
          Порт сопряжения
          <input type="text" inputMode="numeric" value={pairPort} onChange={(e) => setPairPort(e.target.value)} placeholder="37145" />
        </label>
        <label className="field" style={{ width: 130 }}>
          Код
          <input type="text" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value)} placeholder="482913" />
        </label>
        <label className="field" style={{ width: 130 }}>
          Порт подключения
          <input type="text" inputMode="numeric" value={connectPort} onChange={(e) => setConnectPort(e.target.value)} placeholder="41827" />
        </label>
        <button className="btn primary" onClick={pair} disabled={!valid || busy}>
          {busy ? 'Сопряжение…' : 'Сопрячь и подключить'}
        </button>
      </div>
    </div>
  )
}
