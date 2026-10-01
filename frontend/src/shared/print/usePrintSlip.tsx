import { useState, type ReactNode } from 'react'
import { createPortal, flushSync } from 'react-dom'

/**
 * FR-08.9: prints one slip alone. The slip joins the page only until the print dialog closes, and the print CSS hides
 * everything else, so the paper shows the slip and nothing of the screen. Render `area` anywhere in the page.
 */
export function usePrintSlip() {
  const [slip, setSlip] = useState<ReactNode>(null)
  const print = (node: ReactNode) => {
    flushSync(() => setSlip(node))
    window.addEventListener('afterprint', () => setSlip(null), { once: true })
    window.print()
  }
  const area = slip ? createPortal(<div className="print-area">{slip}</div>, document.body) : null
  return { print, area }
}
