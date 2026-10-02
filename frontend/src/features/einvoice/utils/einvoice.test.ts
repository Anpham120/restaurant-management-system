import { describe, expect, it } from 'vitest'
import { isInvoiceNo, isSymbol, isTaxCode } from './einvoice'

describe('buyer of an e-invoice (BR-46)', () => {
  it('takes a tax code of 10 digits, with or without a branch, or of 12 digits', () => {
    expect(isTaxCode('0101234567')).toBe(true)
    expect(isTaxCode('0101234567-001')).toBe(true)
    expect(isTaxCode('001099012345')).toBe(true)
  })

  it('refuses any other tax code', () => {
    expect(isTaxCode('12345')).toBe(false)
    expect(isTaxCode('01012345678')).toBe(false)
    expect(isTaxCode('0101234567-01')).toBe(false)
  })
})

describe('number of an issued e-invoice (BR-46)', () => {
  it('has a symbol of 7 characters, in any case', () => {
    expect(isSymbol('1C26MKB')).toBe(true)
    expect(isSymbol('1c26mkb')).toBe(true)
    expect(isSymbol('ABC')).toBe(false)
    expect(isSymbol('1X26MKB')).toBe(false)
  })

  it('has a number of 1 to 8 digits, leading zeros aside', () => {
    expect(isInvoiceNo('123')).toBe(true)
    expect(isInvoiceNo('00000123')).toBe(true)
    expect(isInvoiceNo('0')).toBe(false)
    expect(isInvoiceNo('123456789')).toBe(false)
  })
})
