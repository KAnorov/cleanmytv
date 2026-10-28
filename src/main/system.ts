// TV settings outside the launcher, all from upstream README: the Clock screensaver, ad-blocking
// Private DNS, animation speed and the launcher's own language.
import { Socket } from 'node:net'
import * as T from './tv'
import { PKG } from './launcher'
import type { ApplyResult, SystemState } from '@shared/api'

const TASK = 'system'
const CLOCK = `${PKG}/.ClockDream`

export async function state(): Promise<SystemState> {
  const get = async (ns: string, key: string): Promise<string> => (await T.tv().shell(`settings get ${ns} ${key}`)).trim()
  return {
    sdk: T.getStatus().probe?.sdk ?? 0,
    screensaverIsClock: (await get('secure', 'screensaver_components')).includes(CLOCK),
    privateDnsMode: await get('global', 'private_dns_mode'),
    privateDnsHost: await get('global', 'private_dns_specifier'),
    animationScale: await get('global', 'window_animation_scale')
  }
}

export async function clockScreensaver(on: boolean): Promise<ApplyResult> {
  if (on) {
    await T.sh(TASK, `settings put secure screensaver_components ${CLOCK}; settings put secure screensaver_enabled 1`)
    return { ok: true, summary: 'Заставка с часами включена.', warnings: [] }
  }
  // Back to the TV's own choice: an empty value makes Android use its default dream.
  await T.sh(TASK, 'settings delete secure screensaver_components')
  return { ok: true, summary: 'Штатная заставка TV снова включена.', warnings: [] }
}

export async function privateDns(host: string | null): Promise<ApplyResult> {
  if (!host) {
    await T.sh(TASK, 'settings put global private_dns_mode opportunistic')
    return { ok: true, summary: 'Private DNS снова работает автоматически.', warnings: [] }
  }
  T.q(host)
  if (!/^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(host)) throw new Error(`это не имя DNS-хоста: ${host}`)
  if ((T.getStatus().probe?.sdk ?? 0) < 28) throw new Error('Private DNS требует Android 9 или новее.')
  await T.sh(TASK, `settings put global private_dns_specifier ${host}`)
  await T.sh(TASK, 'settings put global private_dns_mode hostname')
  return {
    ok: true,
    summary: `DNS всех приложений теперь идёт через ${host}.`,
    warnings: ['Если приложение перестало воспроизводить или TV пишет «Нет интернета», отключите Private DNS здесь.']
  }
}

export async function animationScale(scale: number): Promise<ApplyResult> {
  if (![0, 0.5, 1].includes(scale)) throw new Error('скорость должна быть 0, 0.5 или 1')
  for (const k of ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']) {
    await T.sh(TASK, `settings put global ${k} ${scale}`)
  }
  return { ok: true, summary: scale === 0 ? 'Системные анимации отключены.' : `Системные анимации работают на скорости ${scale}x.`, warnings: [] }
}

export async function launcherLocale(tag: string): Promise<ApplyResult> {
  if (tag && !/^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/.test(tag)) throw new Error(`это не языковой тег: ${tag}`)
  if ((T.getStatus().probe?.sdk ?? 0) < 33) throw new Error('Отдельный язык для приложения требует Android 13 или новее; лаунчер следует языку TV.')
  await T.sh(TASK, `cmd locale set-app-locales ${PKG} --locales ${tag || '""'}`)
  await T.sh(TASK, `am force-stop ${PKG}; am start -n ${PKG}/.MainActivity >/dev/null`)
  return { ok: true, summary: tag ? `Лаунчер теперь показывает ${tag}.` : 'Лаунчер снова следует языку TV.', warnings: [] }
}

export async function key(code: number): Promise<void> {
  if (!Number.isInteger(code) || code < 0 || code > 400) throw new Error('неверный код кнопки')
  await T.tv().shell(`input keyevent ${code}`)
}

export async function reboot(): Promise<void> {
  await T.tv().shell('reboot').catch(() => undefined)
}

/** Upstream tools/adb-closed.py: refused or silent means closed. */
export function adbClosed(host: string): Promise<{ closed: boolean; message: string }> {
  return new Promise((resolve) => {
    const s = new Socket()
    const done = (closed: boolean, message: string): void => { s.destroy(); resolve({ closed, message }) }
    s.setTimeout(3000)
    s.once('connect', () => done(false, `${host}:5555 всё ещё открыт: отладка по сети включена. На TV выключите отладку по USB и по сети/ADB, перезагрузите TV и проверьте снова.`))
    s.once('timeout', () => done(true, `${host}:5555 не ответил за 3 с: порт закрыт, если TV включён и находится по этому адресу.`))
    s.once('error', (e: NodeJS.ErrnoException) => {
      if (e.code === 'ECONNREFUSED') done(true, `${host}:5555 закрыт: TV отклонил подключение.`)
      else done(false, `Не удалось проверить ${host}:5555 (${e.code ?? e.message}). TV включён и в этой сети?`)
    })
    s.connect(5555, host)
  })
}
