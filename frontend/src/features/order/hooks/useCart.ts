import { useMemo, useState } from 'react'
import type { ItemLine, MenuItem } from '@/shared/api/types'

export interface CartLine {
  item: MenuItem
  quantity: number
  note: string
}

/** Dishes chosen but not sent yet. Sending is always an explicit button press. */
export function useCart() {
  const [lines, setLines] = useState<CartLine[]>([])

  return useMemo(() => {
    const change = (item: MenuItem, delta: number) =>
      setLines((current) => {
        const existing = current.find((l) => l.item.id === item.id)
        if (!existing) return delta > 0 ? [...current, { item, quantity: delta, note: '' }] : current
        const quantity = Math.min(50, existing.quantity + delta)
        return quantity <= 0
          ? current.filter((l) => l.item.id !== item.id)
          : current.map((l) => (l.item.id === item.id ? { ...l, quantity } : l))
      })

    return {
      lines,
      count: lines.reduce((sum, l) => sum + l.quantity, 0),
      total: lines.reduce((sum, l) => sum + l.quantity * l.item.price, 0),
      quantities: Object.fromEntries(lines.map((l) => [l.item.id, l.quantity])) as Record<number, number>,
      add: (item: MenuItem) => change(item, 1),
      remove: (item: MenuItem) => change(item, -1),
      setNote: (itemId: number, note: string) =>
        setLines((current) => current.map((l) => (l.item.id === itemId ? { ...l, note } : l))),
      clear: () => setLines([]),
      toItemLines: (): ItemLine[] =>
        lines.map((l) => ({ menuItemId: l.item.id, quantity: l.quantity, note: l.note.trim() || undefined })),
    }
  }, [lines])
}

export type Cart = ReturnType<typeof useCart>
