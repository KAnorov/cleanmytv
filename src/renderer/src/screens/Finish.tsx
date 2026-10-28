import { useEffect, useState, type ReactNode } from 'react'
import type { ScreenProps } from '../App'
import { Busy } from '../components/ui'

const STEPS = [
  'На TV откройте «Для разработчиков» и выключите «Отладку по USB».',
  'Выключите «Отладку по сети», «Отладку ADB» или «Отладку по Wi-Fi», если они есть.',
  'По желанию: выберите «Отозвать разрешения на отладку по USB», чтобы ни один компьютер больше не считался доверенным. Пропустите, если не хотите заново подтверждать доступ.',
  'Перезагрузите TV.'
]

export function Finish({ status, markDone }: ScreenProps): ReactNode {
  const [host, setHost] = useState(status.host ?? '')
  const [ticks, setTicks] = useState<boolean[]>(STEPS.map(() => false))
  const [checking, setChecking] = useState(false)
  const [res, setRes] = useState<{ closed: boolean; message: string } | null>(null)

  useEffect(() => {
    if (!host) window.cytv.lastHost().then((h) => h && setHost(h))
  }, [])

  async function check(): Promise<void> {
    setChecking(true)
    setRes(null)
    try {
      const r = await window.cytv.checkAdbClosed(host.trim())
      setRes(r)
      if (r.closed) markDone('finish')
    } catch (e) {
      setRes({ closed: false, message: e instanceof Error ? e.message : String(e) })
    } finally {
      setChecking(false)
    }
  }

  return (
    <>
      <h1>Завершение и защита</h1>
      <p className="lede">
        Отладка по сети — это консоль без шифрования на порту 5555. На некоторых TV любое устройство в вашей Wi-Fi сети
        может использовать её без запроса разрешения. Закройте её, когда закончите. Лаунчер, очистка и тема продолжат
        работать. Включите отладку снова, когда захотите что-то изменить из этого приложения.
      </p>

      <div className="card">
        <h2>На TV</h2>
        <ul className="checklist">
          {STEPS.map((s, i) => (
            <li key={i} className="check" style={{ gridTemplateColumns: 'auto 1fr' }}>
              <label className="row" style={{ cursor: 'pointer', gridColumn: '1 / 3' }}>
                <input type="checkbox" checked={ticks[i]} onChange={(e) => setTicks(ticks.map((t, j) => (j === i ? e.target.checked : t)))} />
                <span style={{ textDecoration: ticks[i] ? 'line-through' : undefined, opacity: ticks[i] ? 0.6 : 1 }}>{s}</span>
              </label>
            </li>
          ))}
        </ul>
        {status.state === 'connected' && (
          <p className="small muted" style={{ marginTop: 10 }}>
            Приложение всё ещё подключено. Выключение отладки оборвёт соединение — это нормально.
          </p>
        )}
      </div>

      <div className="card white">
        <h2>Возвращение позже</h2>
        <p className="muted" style={{ marginBottom: 8 }}>
          Чтобы изменить обои, порядок приложений или другие настройки в разделе «Настройка»:
        </p>
        <ol className="small" style={{ margin: '0 0 8px', paddingLeft: 20 }}>
          <li>На TV снова включите «Отладку по USB» (и «Отладку по сети» или «Отладку ADB», если они есть).</li>
          <li>Откройте это приложение. Оно само подключится к TV, найдёт его по новому адресу и откроет «Настройку».</li>
          <li>Если вы отозвали разрешения, на TV снова выберите «Разрешить».</li>
          <li>Примените изменения и снова выключите отладку.</li>
        </ol>
        <p className="small muted" style={{ margin: 0 }}>
          Перед редактированием приложение читает текущее состояние TV, поэтому вы всегда начинаете с актуальных обоев и
          порядка. Некоторые Google TV, которым нужен код сопряжения, закрывают порт 5555 после перезагрузки; тогда
          приложение снова покажет форму сопряжения.
        </p>
      </div>

      <div className="card white">
        <h2>Проверка с этого компьютера</h2>
        <p className="muted">После перезагрузки TV проверьте, что порт 5555 больше не отвечает.</p>
        <div className="row">
          <input type="text" value={host} onChange={(e) => setHost(e.target.value)} placeholder="192.168.1.50" style={{ width: 200 }} />
          <button className="btn primary" onClick={check} disabled={checking || !/^\d{1,3}(\.\d{1,3}){3}$/.test(host.trim())}>
            Проверить, что порт 5555 закрыт
          </button>
          {checking && <Busy text="Стучимся в порт 5555" />}
        </div>
        {res && (
          <div className={`notice ${res.closed ? 'ok' : 'warn'}`} style={{ marginTop: 16, marginBottom: 0 }}>
            <b>{res.closed ? 'Закрыт.' : 'Всё ещё открыт.'}</b> {res.message}
          </div>
        )}
      </div>
    </>
  )
}
