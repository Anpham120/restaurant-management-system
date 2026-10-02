import type { Channel, MenuItem, Order } from '@/shared/api/types'

/** BR-47: the dishes an app sells, at the price on that app. */
export function forChannel(items: MenuItem[], channel: Channel): MenuItem[] {
  return items.flatMap((m) => {
    const price = m.appPrices[channel]
    return price === undefined ? [] : [{ ...m, price }]
  })
}

/** FR-21.3: the shipper can take the order once no dish is still waiting or cooking, and there is a dish to take. */
export function readyForShipper(o: Pick<Order, 'items'>): boolean {
  const billed = o.items.filter((i) => i.status !== 'CANCELLED' && i.status !== 'PENDING')
  return billed.length > 0 && billed.every((i) => i.status === 'READY' || i.status === 'SERVED')
}
