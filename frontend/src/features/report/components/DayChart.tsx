import dayjs from 'dayjs'
import type { ReportSummary } from '@/shared/api/types'
import { money } from '@/shared/utils/format'

const WIDTH = 640
const HEIGHT = 180
const LABELS = 18
const GAP = 2

/** FR-10.6: revenue by day as columns, the best day at full height; hovering a column shows its numbers. */
export default function DayChart({ days }: { days: ReportSummary['byDay'] }) {
  const best = Math.max(1, ...days.map((d) => d.amount))
  const column = (WIDTH - GAP * (days.length - 1)) / days.length
  // At most about ten dates under the columns, so they never run into each other.
  const every = Math.ceil(days.length / 10)

  return (
    <svg viewBox={`0 0 ${WIDTH} ${HEIGHT + LABELS}`} role="img" aria-label="Biểu đồ doanh thu theo ngày" className="day-chart">
      {days.map((d, i) => {
        const x = i * (column + GAP)
        const height = (d.amount / best) * HEIGHT
        return (
          <g key={d.date}>
            <rect x={x} y={HEIGHT - height} width={column} height={height} rx={2} className="day-chart-bar">
              <title>{`${dayjs(d.date).format('DD/MM')}: ${money(d.amount)}, ${d.count} đơn`}</title>
            </rect>
            {i % every === 0 && (
              <text x={x + column / 2} y={HEIGHT + 14} textAnchor="middle" className="day-chart-label">
                {dayjs(d.date).format('DD/MM')}
              </text>
            )}
          </g>
        )
      })}
    </svg>
  )
}
