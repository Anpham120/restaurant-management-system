import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'
import type { ItemLine, MenuItem, MenuSection } from '../api/types'
import CartPanel from './CartPanel'
import MenuPicker from './MenuPicker'
import { useCart } from './useCart'

const dish = (id: number, categoryId: number, name: string, price: number, available = true): MenuItem => ({
  id,
  categoryId,
  categoryName: categoryId === 1 ? 'Món chính' : 'Đồ uống',
  name,
  price,
  description: null,
  available,
})

const SECTIONS: MenuSection[] = [
  {
    categoryId: 1,
    categoryName: 'Món chính',
    items: [dish(1, 1, 'Nem rán', 65_000), dish(2, 1, 'Gỏi cuốn', 55_000), dish(3, 1, 'Lẩu riêu cua', 320_000, false)],
  },
  { categoryId: 2, categoryName: 'Đồ uống', items: [dish(4, 2, 'Trà đá', 5_000)] },
]

/** The menu and the cart side by side, as on the order page and the guest page. */
function Ordering({ onSend }: { onSend: (lines: ItemLine[]) => void }) {
  const cart = useCart()
  return (
    <>
      <MenuPicker sections={SECTIONS} cart={cart} />
      <section aria-label="Giỏ">
        <CartPanel cart={cart} sendLabel="Gửi món" sending={false} onSend={() => onSend(cart.toItemLines())} />
      </section>
    </>
  )
}

const cart = () => within(screen.getByRole('region', { name: 'Giỏ' }))
const add = (name: string) => fireEvent.click(screen.getByRole('button', { name: `Thêm ${name}` }))

describe('ordering from the menu into the cart', () => {
  it('starts with an empty cart', () => {
    render(<Ordering onSend={vi.fn()} />)
    expect(cart().getByText('Chưa chọn món')).toBeTruthy()
  })

  it('adds dishes and shows the running total and count', () => {
    render(<Ordering onSend={vi.fn()} />)
    add('Nem rán')
    add('Nem rán')
    add('Gỏi cuốn')
    expect(cart().getByText('Nem rán')).toBeTruthy()
    expect(cart().getByText('185.000 đ')).toBeTruthy()
    expect(cart().getByRole('button', { name: 'Gửi món (3)' })).toBeTruthy()
  })

  it('drops a line when its quantity goes back to zero', () => {
    render(<Ordering onSend={vi.fn()} />)
    add('Nem rán')
    add('Gỏi cuốn')
    const [, goiCuon] = cart().getAllByRole('button', { name: 'Bớt' })
    fireEvent.click(goiCuon)
    expect(cart().queryByText('Gỏi cuốn')).toBeNull()
    expect(cart().getByRole('button', { name: 'Gửi món (1)' })).toBeTruthy()
  })

  it('sends each line with its trimmed note, and no note when it is blank', () => {
    const onSend = vi.fn()
    render(<Ordering onSend={onSend} />)
    add('Nem rán')
    add('Nem rán')
    add('Gỏi cuốn')
    const [nemNote] = cart().getAllByPlaceholderText(/Ghi chú/)
    fireEvent.change(nemNote, { target: { value: '  ít cay ' } })
    fireEvent.click(cart().getByRole('button', { name: 'Gửi món (3)' }))
    expect(onSend).toHaveBeenCalledWith([
      { menuItemId: 1, quantity: 2, note: 'ít cay' },
      { menuItemId: 2, quantity: 1, note: undefined },
    ])
  })

  it('does not let a sold-out dish be added', () => {
    render(<Ordering onSend={vi.fn()} />)
    expect(screen.getByText('Hết món')).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Thêm Lẩu riêu cua' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('switches between categories', () => {
    render(<Ordering onSend={vi.fn()} />)
    expect(screen.queryByText('Trà đá')).toBeNull()
    fireEvent.click(screen.getByText('Đồ uống'))
    expect(screen.getByText('Trà đá')).toBeTruthy()
    expect(screen.queryByText('Nem rán')).toBeNull()
  })
})
