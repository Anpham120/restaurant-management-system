import { useQuery } from '@tanstack/react-query'
import { Card, Col, Row, Table, Typography } from 'antd'
import dayjs from 'dayjs'
import { api } from '../api/client'
import type { ShiftAssignment } from '../api/types'
import { hhmm } from '../utils/hr'

/** Self-service for every employee: own schedule (FR-13.4). */
export default function MePage() {
  const from = dayjs().format('YYYY-MM-DD')
  const to = dayjs().add(13, 'day').format('YYYY-MM-DD')

  const schedule = useQuery({
    queryKey: ['me', 'schedule', from],
    queryFn: () => api.get<ShiftAssignment[]>('/me/schedule', { params: { from, to } }).then((r) => r.data),
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Của tôi</Typography.Title>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="Lịch làm 2 tuần tới" size="small">
            <Table<ShiftAssignment>
              size="small"
              rowKey="id"
              pagination={false}
              loading={schedule.isLoading}
              dataSource={schedule.data ?? []}
              locale={{ emptyText: 'Chưa được xếp ca' }}
              columns={[
                { title: 'Ngày', render: (_, a) => dayjs(a.workDate).format('dd DD/MM') },
                { title: 'Ca', dataIndex: 'shiftName' },
                { title: 'Giờ', render: (_, a) => `${hhmm(a.startTime)}–${hhmm(a.endTime)}` },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </>
  )
}
