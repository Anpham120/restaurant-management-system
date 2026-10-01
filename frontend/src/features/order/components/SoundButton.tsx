import { useEffect, useSyncExternalStore } from 'react'
import { Button } from 'antd'
import { MutedOutlined, SoundOutlined } from '@ant-design/icons'
import { onSoundStateChange, soundLocked, unlockSound } from '../utils/sound'

/**
 * Turns alert sounds on or off on this device (FR-07.5). Browsers stay silent until the page is tapped, so while
 * sound is on but still blocked, the button asks for a tap.
 */
export default function SoundButton({ on, onChange }: { on: boolean; onChange: (on: boolean) => void }) {
  const locked = useSyncExternalStore(onSoundStateChange, soundLocked)

  // Any tap or key press on the page lets sound play.
  useEffect(() => {
    window.addEventListener('click', unlockSound)
    window.addEventListener('keydown', unlockSound)
    return () => {
      window.removeEventListener('click', unlockSound)
      window.removeEventListener('keydown', unlockSound)
    }
  }, [])

  if (!on) return <Button icon={<MutedOutlined />} onClick={() => onChange(true)} title="Bật âm báo" />
  if (locked) {
    return (
      <Button icon={<SoundOutlined />} danger onClick={unlockSound}>
        Chạm để bật âm báo
      </Button>
    )
  }
  return <Button icon={<SoundOutlined />} onClick={() => onChange(false)} title="Tắt âm báo" />
}
