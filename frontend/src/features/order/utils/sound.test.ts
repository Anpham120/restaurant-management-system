import { describe, expect, it } from 'vitest'
import { pageAlerts } from './sound'

describe('pageAlerts', () => {
  it('rings the kitchen for dishes that reach it', () => {
    expect(pageAlerts('/kitchen')).toEqual(['NEW_DISHES'])
  })

  it('rings waiters for finished dishes, QR orders and guest calls, on the floor plan and in an order', () => {
    expect(pageAlerts('/tables')).toEqual(['DISH_READY', 'GUEST_DISHES', 'SERVICE_REQUEST'])
    expect(pageAlerts('/orders/12')).toEqual(['DISH_READY', 'GUEST_DISHES', 'SERVICE_REQUEST'])
  })

  it('keeps every other page quiet', () => {
    for (const page of ['/cashier', '/admin/tables', '/admin/settings', '/me']) {
      expect(pageAlerts(page)).toEqual([])
    }
  })
})
