/** FR-09.10: what stock counts found against the books; less is a shortfall worth a look. Empty when they agreed. */
export function countDifference(adjusted: number, unit: string): string {
  if (adjusted === 0) return ''
  return `${adjusted < 0 ? 'Thiếu' : 'Dư'} ${Math.abs(adjusted)} ${unit}`
}
