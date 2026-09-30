import type { StaffAlert } from '../api/types'

/**
 * Which alerts ring on a page (FR-07.5). The kitchen screen rings for dishes that reach it; the floor plan and the
 * order page ring for finished dishes and for dishes a guest sent from the table QR code. Other pages stay quiet.
 */
export function pageAlerts(pathname: string): StaffAlert[] {
  if (pathname.startsWith('/kitchen')) return ['NEW_DISHES']
  if (pathname.startsWith('/tables') || pathname.startsWith('/orders')) return ['DISH_READY', 'GUEST_DISHES']
  return []
}

const SOUND_KEY = 'bnn.sound'

/** Sound is on unless someone turned it off on this device. */
export function soundWanted(): boolean {
  try {
    return localStorage.getItem(SOUND_KEY) !== 'off'
  } catch {
    return true
  }
}

export function setSoundWanted(on: boolean) {
  try {
    if (on) localStorage.removeItem(SOUND_KEY)
    else localStorage.setItem(SOUND_KEY, 'off')
  } catch {
    // Private mode without storage: the choice lasts until reload.
  }
}

/** Pitch of each beep in Hz, so staff can tell the alerts apart by ear. */
const TONES: Record<StaffAlert, number[]> = {
  NEW_DISHES: [880, 660],
  GUEST_DISHES: [660, 880],
  DISH_READY: [988, 988, 988],
}

let context: AudioContext | undefined

function audio(): AudioContext {
  context ??= new AudioContext()
  return context
}

/** Browsers keep a page silent until someone taps it or presses a key: call this from such an event. */
export function unlockSound() {
  void audio().resume()
}

/** True while the browser still blocks sound on this page. */
export function soundLocked(): boolean {
  return audio().state !== 'running'
}

export function onSoundStateChange(listener: () => void): () => void {
  const ctx = audio()
  ctx.addEventListener('statechange', listener)
  return () => ctx.removeEventListener('statechange', listener)
}

/** Short beeps made in the browser, so there is no sound file to ship. */
export function ring(alert: StaffAlert) {
  const ctx = audio()
  if (ctx.state !== 'running') return
  TONES[alert].forEach((frequency, i) => {
    const start = ctx.currentTime + i * 0.2
    const beep = ctx.createOscillator()
    const volume = ctx.createGain()
    beep.frequency.value = frequency
    volume.gain.setValueAtTime(0.3, start)
    volume.gain.exponentialRampToValueAtTime(0.001, start + 0.18)
    beep.connect(volume).connect(ctx.destination)
    beep.start(start)
    beep.stop(start + 0.2)
  })
}
