/** A table for Excel: cells split by tabs, rows by CRLF; tabs and line breaks inside a cell become spaces. */
export function toSheet(rows: (string | number)[][]): string {
  return rows.map((row) => row.map((cell) => String(cell).replace(/[\t\r\n]+/g, ' ')).join('\t')).join('\r\n')
}

/** UTF-16LE with a byte-order mark: Excel opens it as a table whatever the Windows language. */
export function utf16leWithBom(text: string): Uint8Array<ArrayBuffer> {
  const bytes = new Uint8Array(2 + text.length * 2)
  bytes[0] = 0xff
  bytes[1] = 0xfe
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i)
    bytes[2 + i * 2] = code & 0xff
    bytes[3 + i * 2] = code >> 8
  }
  return bytes
}

/** Saves a file the server made, such as an .xlsx export. */
export function downloadBlob(fileName: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

/** Saves a sheet as a file Excel opens straight away. */
export function downloadSheet(fileName: string, text: string) {
  const url = URL.createObjectURL(new Blob([utf16leWithBom(text)], { type: 'text/csv' }))
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}
