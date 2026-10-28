import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { ApplyResult, DebloatEntry, DebloatListSummary, DebloatPlan, PackageState } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, Confirm, ResultNotice, Toggle, errorText } from '../components/ui'

type Filter = 'all' | 'disable' | 'disabled' | 'absent'

export function Debloat({ status, go, markDone }: ScreenProps): ReactNode {
  const [lists, setLists] = useState<DebloatListSummary[] | null>(null)
  const [query, setQuery] = useState('')
  const [listId, setListId] = useState<string | null>(null)
  const [plan, setPlan] = useState<DebloatPlan | null>(null)
  const [loadingPlan, setLoadingPlan] = useState(false)
  const [ticked, setTicked] = useState<Set<string>>(new Set())
  const [filter, setFilter] = useState<Filter>('all')
  const [confirm, setConfirm] = useState<'apply' | 'restore' | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [forceHome, setForceHome] = useState(false)
  const [forceSetup, setForceSetup] = useState(false)
  const [showAdvanced, setShowAdvanced] = useState(false)

  useEffect(() => {
    window.cytv.lists().then((l) => {
      const sorted = [...l].sort((a, b) => b.score - a.score || a.title.localeCompare(b.title))
      setLists(sorted)
      const best = sorted.find((x) => x.score > 0)
      if (best) setListId(best.id)
    })
  }, [status.host])

  async function loadPlan(id: string): Promise<void> {
    setLoadingPlan(true)
    setResult(null)
    try {
      const p = await window.cytv.plan(id)
      setPlan(p)
      setTicked(new Set(p.entries.filter((e) => defaultTick(e)).map((e) => e.pkg)))
    } catch (e) {
      setPlan(null)
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setLoadingPlan(false)
    }
  }

  useEffect(() => {
    if (listId) loadPlan(listId)
  }, [listId])

  const toDisable = useMemo(() => plan?.entries.filter((e) => ticked.has(e.pkg) && e.state === 'enabled') ?? [], [plan, ticked])
  const toEnable = useMemo(
    () => plan?.entries.filter((e) => !ticked.has(e.pkg) && e.state === 'disabled' && e.recorded === 'enabled') ?? [],
    [plan, ticked]
  )
  const counts = useMemo(() => {
    const e = plan?.entries ?? []
    return {
      all: e.length,
      disable: e.filter((x) => ticked.has(x.pkg) && x.state === 'enabled').length,
      disabled: e.filter((x) => x.state === 'disabled').length,
      absent: e.filter((x) => x.state === 'absent').length
    }
  }, [plan, ticked])

  const shown = useMemo(() => {
    const e = plan?.entries ?? []
    if (filter === 'disable') return e.filter((x) => ticked.has(x.pkg) && x.state === 'enabled')
    if (filter === 'disabled') return e.filter((x) => x.state === 'disabled')
    if (filter === 'absent') return e.filter((x) => x.state === 'absent')
    return e
  }, [plan, ticked, filter])

  const visibleLists = useMemo(() => {
    if (!lists) return []
    const q = query.trim().toLowerCase()
    return q ? lists.filter((l) => l.title.toLowerCase().includes(q) || l.id.includes(q)) : lists
  }, [lists, query])

  function toggle(pkg: string, on: boolean): void {
    setTicked((prev) => {
      const n = new Set(prev)
      if (on) n.add(pkg)
      else n.delete(pkg)
      return n
    })
  }

  async function apply(): Promise<void> {
    if (!plan) return
    setConfirm(null)
    setBusy('Применяем список на TV')
    try {
      const r = await window.cytv.applyDebloat({
        listId: plan.listId,
        disable: toDisable.map((e) => e.pkg),
        enable: toEnable.map((e) => e.pkg),
        forceHome,
        forceSetupwraith: forceSetup
      })
      setResult(r)
      if (r.ok) markDone('debloat')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      loadPlan(plan.listId)
    }
  }

  async function restore(): Promise<void> {
    if (!plan) return
    setConfirm(null)
    setBusy('Восстанавливаем записанное состояние')
    try {
      setResult(await window.cytv.restore(plan.listId))
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      loadPlan(plan.listId)
    }
  }

  const stockHome = plan?.homePackage && plan.homePackage !== 'com.gathin.tvhome' && plan.entries.some((e) => e.pkg === plan.homePackage && ticked.has(e.pkg))
  const selectedList = lists?.find((l) => l.id === listId)

  return (
    <>
      <h1>Очистка</h1>
      <p className="lede">
        Выберите список для своего TV и снимите галочки с того, чем пользуетесь. Пакеты отключаются только для вашего
        пользователя и никогда не удаляются. Перед первым изменением приложение запоминает состояние TV, поэтому
        «Восстановить» вернёт все пакеты как было.
      </p>

      <div className="card">
        <div className="card-head">
          <div>
            <h2>Какой список?</h2>
            <p className="muted small" style={{ margin: 0 }}>
              {selectedList
                ? selectedList.score > 0
                  ? 'Этот список подходит вашему TV. Выберите другой, если знаете лучше.'
                  : 'Точного совпадения с вашей моделью нет. Список той же марки и года — хорошая отправная точка.'
                : 'Списки составлены людьми, которые чистили такую же модель TV.'}
            </p>
          </div>
          <input type="search" placeholder="Поиск по бренду или модели" value={query} onChange={(e) => setQuery(e.target.value)} style={{ width: 240 }} />
        </div>
        {!lists ? (
          <Busy text="Загрузка списков" />
        ) : (
          <div className="list-picker" role="listbox">
            {visibleLists.map((l) => (
              <button
                key={l.id}
                role="option"
                aria-selected={l.id === listId}
                className={`list-opt${l.id === listId ? ' selected' : ''}`}
                onClick={() => setListId(l.id)}
              >
                {l.score > 0 && <span className="chip ok">Совпадение</span>}
                {l.id === 'custom' && <span className="chip">Ваш TV</span>}
                <span className="title">{l.title}</span>
                {l.id !== 'custom' && <span className="id">{l.id}</span>}
              </button>
            ))}
            {visibleLists.length === 0 && <p className="muted">Нет списков по запросу «{query}».</p>}
          </div>
        )}
      </div>

      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {busy && (
        <div className="notice">
          <Busy text={busy} />
        </div>
      )}

      {loadingPlan && <Busy text="Читаем установленные на TV пакеты" />}

      {plan && !loadingPlan && (
        <div className="card white">
          <div className="card-head">
            <div>
              <h2>{plan.title}</h2>
              {plan.header.length > 0 && (
                <details>
                  <summary className="small muted" style={{ cursor: 'pointer' }}>
                    Заметки автора списка
                  </summary>
                  <pre className="mono small" style={{ whiteSpace: 'pre-wrap', margin: '8px 0 0', color: 'var(--navy)' }}>
                    {plan.header.join('\n')}
                  </pre>
                </details>
              )}
            </div>
          </div>

          <div className="stat-row">
            <div className="stat">
              <b>{counts.disable}</b>
              <span>будет отключено</span>
            </div>
            <div className="stat">
              <b>{toEnable.length}</b>
              <span>будет включено</span>
            </div>
            <div className="stat">
              <b>{counts.disabled}</b>
              <span>уже отключено</span>
            </div>
            <div className="stat">
              <b>{counts.absent}</b>
              <span>нет на этом TV</span>
            </div>
          </div>

          {plan.record ? (
            <div className="notice">
              Точка восстановления сохранена {fmtDate(plan.record.created)} в <code>{plan.record.path}</code>. Сохраните
              этот файл: это единственная копия.
            </div>
          ) : (
            <div className="notice">Точки восстановления пока нет. Приложение сохранит её перед отключением первого пакета.</div>
          )}

          {stockHome && (
            <div className="notice warn">
              Этот список отключает <code>{plan.homePackage}</code>, который сейчас является вашим главным экраном.
              Приложение пропустит его, пока новый лаунчер не станет Home, чтобы TV всегда было чем запуститься.
              Установите лаунчер и примените список снова.
            </div>
          )}

          <div className="toolbar">
            <div className="seg" role="tablist">
              {(
                [
                  ['all', `Все ${counts.all}`],
                  ['disable', `Будет отключено ${counts.disable}`],
                  ['disabled', `Уже отключено ${counts.disabled}`],
                  ['absent', `Не установлено ${counts.absent}`]
                ] as [Filter, string][]
              ).map(([f, label]) => (
                <button key={f} className={filter === f ? 'on' : ''} onClick={() => setFilter(f)}>
                  {label}
                </button>
              ))}
            </div>
            <span className="spacer" />
            <button className="btn small ghost" onClick={() => setTicked(new Set(plan.entries.filter(defaultTick).map((e) => e.pkg)))}>
              Вернуть выбор из списка
            </button>
          </div>

          <div className="table-wrap">
            <table className="pkgs">
              <thead>
                <tr>
                  <th style={{ width: 36 }} />
                  <th>Пакет</th>
                  <th style={{ width: 120 }}>На TV</th>
                  <th>Причина</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((e) => (
                  <tr key={e.pkg} className={e.state === 'absent' ? 'dim' : ''}>
                    <td>
                      <input
                        type="checkbox"
                        aria-label={`Отключить ${e.pkg}`}
                        checked={ticked.has(e.pkg)}
                        disabled={e.state === 'absent'}
                        onChange={(ev) => toggle(e.pkg, ev.target.checked)}
                      />
                    </td>
                    <td>
                      <div className="pkg-name">{e.pkg}</div>
                      {e.choice && <div className="choice-tag">Ваш выбор · {e.choice}</div>}
                      <div className="small muted">
                        {!e.suggested && 'Оставлено автором списка. '}
                        {e.common > 1 && `Отключено в ${e.common} списках.`}
                      </div>
                    </td>
                    <td>
                      <StateBadge state={e.state} recorded={e.recorded} ticked={ticked.has(e.pkg)} />
                    </td>
                    <td className="pkg-note">{e.note || '—'}</td>
                  </tr>
                ))}
                {shown.length === 0 && (
                  <tr>
                    <td colSpan={4} className="muted">
                      Здесь пусто.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div style={{ marginTop: 16 }}>
            <button className="btn small ghost" onClick={() => setShowAdvanced(!showAdvanced)} aria-expanded={showAdvanced}>
              {showAdvanced ? '▾' : '▸'} Обойти защиту
            </button>
            {showAdvanced && (
              <div className="card" style={{ marginTop: 8 }}>
                <Toggle
                  label="Всё равно отключить текущее приложение Home"
                  hint="Только если вы можете управлять TV другим способом. Без лаунчера кнопка Home может открыть тестовое приложение или ничего."
                  checked={forceHome}
                  onChange={setForceHome}
                />
                <Toggle
                  label="Отключить мастер настройки Google TV, даже если ему принадлежит Wi-Fi сеть"
                  hint="Android удаляет сеть, сохранённую при настройке, когда мастер отключается. Используйте только при подключении по Ethernet."
                  checked={forceSetup}
                  onChange={setForceSetup}
                />
              </div>
            )}
          </div>

          <div className="row" style={{ marginTop: 18 }}>
            <button
              className="btn primary"
              disabled={!!busy || (toDisable.length === 0 && toEnable.length === 0)}
              onClick={() => setConfirm('apply')}
            >
              Применить на TV
            </button>
            <button className="btn" disabled={!!busy || !plan.record} onClick={() => setConfirm('restore')}>
              Восстановить
            </button>
            <span className="spacer" />
            <button className="btn ghost" onClick={() => go('launcher')}>
              Далее: установить лаунчер
            </button>
          </div>
        </div>
      )}

      {confirm === 'apply' && plan && (
        <Confirm title="Применить эти изменения?" confirmLabel="Применить" onConfirm={apply} onCancel={() => setConfirm(null)}>
          <p>
            Отключить <b>{toDisable.length}</b> пакетов
            {toEnable.length > 0 && (
              <>
                {' '}и включить <b>{toEnable.length}</b> обратно
              </>
            )}{' '}
            на {status.probe ? `${status.probe.manufacturer} ${status.probe.model}` : status.host}.
          </p>
          <p className="muted small">
            {plan.record ? 'Точка восстановления от первого запуска сохраняется.' : 'Сначала будет сохранена точка восстановления.'}{' '}
            Оставленные приложения обновляются через Play Store как раньше.
          </p>
        </Confirm>
      )}
      {confirm === 'restore' && plan && (
        <Confirm title="Восстановить этот TV?" confirmLabel="Восстановить" onConfirm={restore} onCancel={() => setConfirm(null)}>
          <p>
            Каждый пакет из этого списка вернётся в состояние на {fmtDate(plan.record?.created)}. Пакеты, которые уже были
            отключены тогда, останутся отключёнными. Home вернётся лаунчеру, которому принадлежал.
          </p>
          <p className="muted small">Новый лаунчер останется установленным. Удалите его в Настройках TV → Приложения, если он не нужен.</p>
        </Confirm>
      )}
    </>
  )
}

function defaultTick(e: DebloatEntry): boolean {
  // Keep earlier choices: a package this app switched off stays ticked.
  if (e.state === 'disabled') return e.suggested || e.recorded === 'enabled'
  // Features people use (phone remote, casting, voice) are the user's call, never a default.
  return e.suggested && e.state === 'enabled' && !e.choice
}

function StateBadge({ state, recorded, ticked }: { state: PackageState; recorded?: PackageState; ticked: boolean }): ReactNode {
  if (state === 'absent') return <span className="chip optional">Не установлено</span>
  if (state === 'disabled') {
    if (!ticked && recorded === 'enabled') return <span className="chip warn">Выкл. · включится</span>
    return <span className="chip">Выкл.</span>
  }
  return ticked ? <span className="chip warn">Вкл. · отключится</span> : <span className="chip ok">Вкл. · остаётся</span>
}

function fmtDate(iso?: string): string {
  if (!iso) return 'ранее'
  // recovery.py writes %Y-%m-%dT%H:%M:%S%z, e.g. 2026-10-20T14:03:00+0100.
  const d = new Date(iso.replace(/([+-]\d{2})(\d{2})$/, '$1:$2'))
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })
}
