import { describe, expect, it } from 'vitest'
import { toSheet, utf16leWithBom } from './sheet'

describe('toSheet', () => {
  it('splits cells by tabs and rows by CRLF, keeping each cell on one line', () => {
    expect(toSheet([['Món', 'Doanh thu'], ['Phở\tbò\r\nđặc biệt', 130000], []])).toBe('Món\tDoanh thu\r\nPhở bò đặc biệt\t130000\r\n')
  })
})

describe('utf16leWithBom', () => {
  it('starts with the byte-order mark and stores each character in two bytes', () => {
    expect([...utf16leWithBom('Aă')]).toEqual([0xff, 0xfe, 0x41, 0x00, 0x03, 0x01])
  })
})
