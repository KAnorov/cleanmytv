import { useEffect, useState, type ReactNode } from 'react'
import type { ApplyResult, SystemState } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, Confirm, ResultNotice, Toggle, errorText } from '../components/ui'

const ADGUARD = 'dns.adguard-dns.com'

const LANGUAGES: [string, string][] = [
  ['en', 'English'],
  ['de', 'Deutsch'],
  ['es', 'Español'],
  ['ca', 'Català'],
  ['fr', 'Français'],
  ['ru', 'Русский'],
  ['sr-Latn', 'Srpski (latinica)'],
  ['pl', 'Polski'],
  ['pt', 'Português'],
  ['tr', 'Türkçe'],
  ['ar', 'العربية'],
  ['iw', 'עברית'],
  ['zh-CN', '简体中文'],
  ['zh-TW', '繁體中文 (台灣)'],
  ['th', 'ไทย'],
  ['fi', 'Suomi'],
  ['vi', 'Tiếng Việt'],
  ['bn', 'বাংলা']
]

const SPEEDS: [number, string][] = [
  [0, 'Выкл.'],
  [0.5, 'Быстро (0.5×)'],
  [1, 'Обычно (1×)']
]

export function Extras({ markDone }: ScreenProps): ReactNode {
  const [sys, setSys] = useState<SystemState | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [dnsHost, setDnsHost] = useState('')
  const [lang, setLang] = useState('en')
  const [askReboot, setAskReboot] = useState(false)

  const refresh = (): void => {
    window.cytv.systemState().then((s) => {
      setSys(s)
      if (s.privateDnsMode === 'hostname' && s.privateDnsHost && s.privateDnsHost !== ADGUARD) setDnsHost(s.privateDnsHost)
    })
  }
  useEffect(refresh, [])

  async function run(label: string, fn: () => Promise<ApplyResult>): Promise<void> {
    setBusy(label)
    try {
      const r = await fn()
      setResult(r)
      if (r.ok) markDone('extras')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      refresh()
    }
  }

  if (!sys) return <Busy text="Читаем настройки TV" />

  const dnsOn = sys.privateDnsMode === 'hostname' && !!sys.privateDnsHost
  const scale = Number(sys.animationScale === 'null' ? 1 : sys.animationScale)

  return (
    <>
      <h1>Дополнительно</h1>
      <p className="lede">Настройки вне лаунчера, которые меню TV скрывает или не предлагает.</p>
      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {busy && (
        <div className="notice">
          <Busy text={busy} />
        </div>
      )}

      <div className="grid-2">
        <div className="card">
          <h2>Заставка</h2>
          <Toggle
            label="Заставка с часами"
            hint="Тонкие часы, плавно перемещающиеся по мягкому свечению, чтобы экран не выгорал. Входит в лаунчер."
            checked={sys.screensaverIsClock}
            disabled={!!busy}
            onChange={(v) => run(v ? 'Включаем заставку с часами' : 'Включаем штатную заставку TV', () => window.cytv.setClockScreensaver(v))}
          />
        </div>

        <div className="card">
          <h2>Скорость анимации</h2>
          <p className="muted small">Как быстро открываются меню и приложения на всём TV. На медленных TV «Выкл.» работает быстрее всего.</p>
          <div className="seg">
            {SPEEDS.map(([v, label]) => (
              <button key={v} className={scale === v ? 'on' : ''} disabled={!!busy} onClick={() => run('Меняем скорость анимации', () => window.cytv.setAnimationScale(v))}>
                {label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>Блокировка рекламы внутри приложений</h2>
          <span className={`chip ${dnsOn ? 'ok' : 'optional'}`}>{dnsOn ? `Вкл. · ${sys.privateDnsHost}` : 'Выкл.'}</span>
        </div>
        <p className="muted">
          Направляет Private DNS телевизора на резолвер с блокировкой рекламы, поэтому домены рекламы и трекеров перестают
          разрешаться во всех приложениях. Настройка сохраняется после перезагрузки. Минус: этот резолвер видит все
          DNS-запросы TV. Рекламу в YouTube это не убирает.
        </p>
        {sys.sdk < 28 ? (
          <div className="notice warn">Private DNS требует Android 9 или новее. Этот TV работает на API {sys.sdk}.</div>
        ) : (
          <>
            <div className="row">
              <button className="btn" disabled={!!busy} onClick={() => run('Включаем AdGuard DNS', () => window.cytv.setPrivateDns(ADGUARD))}>
                Использовать AdGuard DNS
              </button>
              <input type="text" value={dnsHost} onChange={(e) => setDnsHost(e.target.value)} placeholder="или другой резолвер, напр. dns.nextdns.io" style={{ width: 280 }} />
              <button
                className="btn"
                disabled={!!busy || !/^[a-z0-9.-]+\.[a-z]{2,}$/i.test(dnsHost.trim())}
                onClick={() => run('Настраиваем Private DNS', () => window.cytv.setPrivateDns(dnsHost.trim()))}
              >
                Использовать этот
              </button>
              {dnsOn && (
                <button className="btn ghost" disabled={!!busy} onClick={() => run('Отключаем Private DNS', () => window.cytv.setPrivateDns(null))}>
                  Отключить
                </button>
              )}
            </div>
            <p className="small muted" style={{ marginTop: 10 }}>
              Если приложение перестало воспроизводить или TV пишет «Нет интернета», значит резолвер недоступен. Отключите
              эту настройку.
            </p>
          </>
        )}
      </div>

      <div className="grid-2">
        <div className="card">
          <h2>Язык лаунчера</h2>
          {sys.sdk >= 33 ? (
            <>
              <p className="muted small">Лаунчер следует языку TV. Android 13 и новее позволяют задать язык только для лаунчера.</p>
              <div className="row">
                <select value={lang} onChange={(e) => setLang(e.target.value)}>
                  {LANGUAGES.map(([tag, name]) => (
                    <option key={tag} value={tag}>
                      {name}
                    </option>
                  ))}
                </select>
                <button className="btn" disabled={!!busy} onClick={() => run('Меняем язык лаунчера', () => window.cytv.setLauncherLocale(lang))}>
                  Установить
                </button>
              </div>
            </>
          ) : (
            <p className="muted small">
              Этот TV работает на Android API {sys.sdk}. Отдельный язык для лаунчера требует Android 13 (API 33).
              Измените язык самого TV в его настройках.
            </p>
          )}
        </div>

        <div className="card">
          <h2>Пульт</h2>
          <p className="muted small">Нажимайте кнопки на TV отсюда — удобно, если пульт в другой комнате.</p>
          <div className="row" style={{ gap: 24, alignItems: 'flex-start' }}>
            <div className="remote" role="group" aria-label="Пульт">
              <span />
              <Key code={19} label="▲" name="Вверх" />
              <span />
              <Key code={21} label="◀" name="Влево" />
              <Key code={23} label="OK" name="OK" round />
              <Key code={22} label="▶" name="Вправо" />
              <span />
              <Key code={20} label="▼" name="Вниз" />
              <span />
            </div>
            <div className="stack" style={{ gap: 6 }}>
              <Key code={3} label="Home" wide />
              <Key code={4} label="Назад" wide />
              <div className="row" style={{ gap: 6 }}>
                <Key code={25} label="Громкость −" wide />
                <Key code={24} label="Громкость +" wide />
              </div>
            </div>
          </div>
          <div style={{ marginTop: 14 }}>
            <button className="btn small ghost" onClick={() => setAskReboot(true)}>
              Перезагрузить TV
            </button>
          </div>
        </div>
      </div>

      {askReboot && (
        <Confirm
          title="Перезагрузить TV?"
          confirmLabel="Перезагрузить"
          onCancel={() => setAskReboot(false)}
          onConfirm={() => {
            setAskReboot(false)
            window.cytv.reboot().then(
              () => setResult({ ok: true, summary: 'TV перезагружается. Когда он вернётся, подключитесь снова в разделе «Подключение».', warnings: [] }),
              (e) => setResult({ ok: false, summary: errorText(e), warnings: [] })
            )
          }}
        >
          <p>Во время перезагрузки соединение оборвётся. На некоторых TV порт 5555 остаётся закрытым до повторного сопряжения.</p>
        </Confirm>
      )}
    </>
  )
}

function Key({ code, label, name, round, wide }: { code: number; label: string; name?: string; round?: boolean; wide?: boolean }): ReactNode {
  return (
    <button
      className={`btn${round ? ' ok-key' : ''}${wide ? ' small' : ''}`}
      aria-label={name ?? label}
      style={wide ? { minWidth: 72, justifyContent: 'center' } : undefined}
      onClick={() => window.cytv.key(code)}
    >
      {label}
    </button>
  )
}
