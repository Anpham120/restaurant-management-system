import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { PaymentInstruction } from '@/shared/api/types'
import TransferQr from './TransferQr'

const INSTRUCTION: PaymentInstruction = {
  paymentId: 7,
  orderId: 3,
  amount: 245_000,
  reference: 'BNN12345678',
  qrImageUrl: 'https://img.vietqr.io/image/970436-0123456789-compact2.png?amount=245000&addInfo=BNN12345678',
  bankCode: '970436',
  bankAccountNo: '0123456789',
  bankAccountName: 'NGUYEN VAN A',
}

describe('TransferQr', () => {
  it('shows the QR, the amount, the reference to keep in the transfer, and the account (BR-15)', () => {
    render(<TransferQr instruction={INSTRUCTION} />)
    const qr = screen.getByRole('img', { name: 'Mã VietQR 245.000 đ' })
    expect(qr.getAttribute('src')).toBe(INSTRUCTION.qrImageUrl)
    expect(screen.getByRole('heading', { name: '245.000 đ' })).toBeTruthy()
    expect(screen.getByText('BNN12345678')).toBeTruthy()
    expect(screen.getByText('NGUYEN VAN A · 0123456789')).toBeTruthy()
  })
})
