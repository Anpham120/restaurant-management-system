/** "8 giờ", "45 phút", "1 giờ 40 phút". */
export function duration(minutes: number | null): string {
  if (minutes === null) return ''
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m} phút`
  return m === 0 ? `${h} giờ` : `${h} giờ ${m} phút`
}
