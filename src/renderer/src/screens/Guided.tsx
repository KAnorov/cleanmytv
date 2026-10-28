// The guided "Clean & launch" run (#9): one button that walks the happy path —
// pick the best-matching list, disable its suggested packages, install the
// bundled launcher, and land on Customise — while keeping every step
// inspectable and skippable. It composes the same IPC calls the individual
// screens use; no new main-process behaviour.
import { useEffect, useRef, useState } from 'react'
import type { ApplyResult, DebloatPlan } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, StatusChip, errorText } from '../components/ui'

type Phase =
  | { t: 'plan' }                       // picking the list, building the plan
  | { t: 'ready'; plan: DebloatPlan; disable: string[] }  // showing what will happen
  | { t: 'run'; step: string }          // executing
  | { t: 'done'; result: ApplyResult; customised: boolean }
  | { t: 'failed'; message: string }

export default function Guided({ go, markDone }: ScreenProps): React.ReactElement {
  const [phase, setPhase] = useState<Phase>({ t: 'plan' })
  const alive = useRef(true)
  useEffect(() => () => { alive.current = false }, [])

  useEffect(() => { if (phase.t === 'plan') void prepare() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function prepare(): Promise<void> {
    try {
      const lists = await window.cytv.lists()
      const best = lists.reduce((a, b) => (b.score > a.score ? b : a))
      if (best.score <= 0) {
        setPhase({ t: 'failed', message: 'Нет списка, близко подходящего к этому TV. Выберите вручную в разделе «Очистка» — для безопасного автоматического запуска нужен подходящий список.' })
        return
      }
      const plan = await window.cytv.plan(best.id)
      const suggested = plan.entries.filter((e) => e.suggested).map((e) => e.pkg)
      if (suggested.length === 0) {
        setPhase({ t: 'failed', message: 'В подходящем списке нет рекомендуемых пакетов. Выберите вручную в разделе «Очистка».' })
        return
      }
      setPhase({ t: 'ready', plan, disable: suggested })
    } catch (e) {
      setPhase({ t: 'failed', message: errorText(e) })
    }
  }

  async function run(skipDebloat: boolean): Promise<void> {
    const p = phase
    if (p.t !== 'ready') return
    setPhase({ t: 'run', step: 'Подготовка' })
    const notes: string[] = []
    try {
      if (!skipDebloat) {
        setPhase({ t: 'run', step: `Отключаем ${p.disable.length} пакетов из «${p.plan.title}»` })
        const r = await window.cytv.applyDebloat({
          listId: p.plan.listId,
          disable: p.disable,
          enable: [],
          forceHome: true,
          forceSetupwraith: true
        })
        if (!r.ok) throw new Error(r.summary)
        notes.push(`Очищено: отключено ${p.disable.length} пакетов (предупреждений: ${r.warnings.length})`)
        if (alive.current) markDone('debloat')
      } else {
        notes.push('Очистка пропущена')
      }

      const before = await window.cytv.launcherStatus()
      if (!before.isHome) {
        setPhase({ t: 'run', step: 'Устанавливаем лаунчер' })
        const r = await window.cytv.install({
          apk: 'bundled',
          stockLauncher: before.stockLauncher ?? 'auto',
          inputKeyService: true,
          homeKeyService: true,
          replaceIncompatible: false
        })
        if (!r.ok) throw new Error(r.summary)
        notes.push('Лаунчер установлен и назначен Home')
        if (alive.current) markDone('launcher')
      } else {
        notes.push('Лаунчер уже Home — оставлен как есть')
      }

      setPhase({ t: 'done', result: { ok: true, summary: notes.join(' · '), warnings: [] }, customised: true })
    } catch (e) {
      notes.push('Остановлено: ' + errorText(e))
      setPhase({ t: 'failed', message: notes.join(' · ') })
    }
  }

  return (
    <>
      <h1>Очистка и запуск</h1>
      <p className="muted">
        Путь одной кнопкой: лучший список для вашего TV, отключение рекомендованных пакетов, установка чистого
        лаунчера и назначение его Home — после чего вы попадаете в «Настройку». Ничего не удаляется, а все шаги
        остаются в боковом меню, если хотите идти медленнее.
      </p>

      {phase.t === 'plan' && <Busy text="Читаем TV и подбираем подходящий список" />}

      {phase.t === 'ready' && (
        <div className="stack" style={{ gap: 16 }}>
          <div className="card">
            <h2 style={{ margin: 0 }}>Готово к запуску</h2>
            <p style={{ margin: '8px 0' }}>
              Список: <b>{phase.plan.title}</b> — ближайшее совпадение для вашего TV.
            </p>
            <p style={{ margin: '8px 0' }}>
              Будет отключено <b>{phase.disable.length} пакетов</b> (рекомендованный набор списка — те же
              значения, что отмечает «Очистка»), затем установится встроенный лаунчер и получит Home.
            </p>
            <p className="muted small" style={{ margin: 0 }}>
              «Восстановить» вернёт все пакеты в текущее состояние; лаунчер можно вернуть к штатному
              в любой момент на шаге «Лаунчер».
            </p>
          </div>
          <div className="row">
            <button className="btn primary" onClick={() => void run(false)}>Очистить и запустить</button>
            <button className="btn" onClick={() => void run(true)}>Только лаунчер (без очистки)</button>
            <button className="btn ghost" onClick={() => go('debloat')}>Выберу вручную</button>
          </div>
        </div>
      )}

      {phase.t === 'run' && <Busy text={phase.step} />}

      {phase.t === 'done' && (
        <div className="stack" style={{ gap: 16 }}>
          <div className="card">
            <StatusChip status="ok" text="Готово" />
            <p style={{ margin: '8px 0 0' }}>{phase.result.summary}</p>
          </div>
          <div className="row">
            <button className="btn primary" onClick={() => go('customise')}>Настроить главный экран</button>
            <button className="btn ghost" onClick={() => go('finish')}>Завершить (закрыть порт отладки)</button>
          </div>
        </div>
      )}

      {phase.t === 'failed' && (
        <div className="stack" style={{ gap: 16 }}>
          <div className="notice error">{phase.message}</div>
          <div className="row">
            <button className="btn" onClick={() => { setPhase({ t: 'plan' }); void prepare() }}>Попробовать снова</button>
            <button className="btn ghost" onClick={() => go('debloat')}>Перейти к пошаговым экранам</button>
          </div>
        </div>
      )}
    </>
  )
}
