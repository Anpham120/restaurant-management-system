import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Form, Input, Modal, Segmented, Space, Table, Tag, Tooltip, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { LeaveRequest } from '@/shared/api/types'
import { leaveDays, leaveStatusColor, leaveStatusLabel, leaveTypeLabel } from '../utils/leave'

/** FR-13.5, FR-13.6: approve or reject leave; approving clears the shifts of those days (BR-24). */
export default function LeavePage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [view, setView] = useState<'pending' | 'all'>('pending')
  const [deciding, setDeciding] = useState<{ leave: LeaveRequest; approve: boolean } | null>(null)

  const leaves = useQuery({
    queryKey: ['leave-requests', view],
    queryFn: () =>
      api.get<LeaveRequest[]>('/leave-requests', { params: { status: view === 'pending' ? 'PENDING' : undefined } }).then((r) => r.data),
  })
  const decide = useMutation({
    mutationFn: (note?: string) => api.post(`/leave-requests/${deciding!.leave.id}/${deciding!.approve ? 'approve' : 'reject'}`, { note }),
    onSuccess: () => {
      message.success(deciding?.approve ? 'Đã duyệt' : 'Đã từ chối')
      setDeciding(null)
      queryClient.invalidateQueries({ queryKey: ['leave-requests'] })
      queryClient.invalidateQueries({ queryKey: ['schedule'] })
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Nghỉ phép</Typography.Title>
        <Segmented<'pending' | 'all'>
          value={view}
          onChange={setView}
          options={[
            { label: 'Chờ duyệt', value: 'pending' },
            { label: 'Tất cả', value: 'all' },
          ]}
        />
      </div>
      <Table<LeaveRequest>
        size="small"
        rowKey="id"
        loading={leaves.isLoading}
        dataSource={leaves.data ?? []}
        locale={{ emptyText: view === 'pending' ? 'Không có đơn chờ duyệt' : 'Chưa có đơn nghỉ' }}
        scroll={{ x: true }}
        columns={[
          { title: 'Nhân viên', dataIndex: 'employeeName' },
          { title: 'Ngày nghỉ', render: (_, l) => leaveDays(l) },
          { title: 'Loại', render: (_, l) => leaveTypeLabel[l.type] },
          { title: 'Lý do', dataIndex: 'reason' },
          { title: 'Trạng thái', render: (_, l) => <Tag color={leaveStatusColor[l.status]}>{leaveStatusLabel[l.status]}</Tag> },
          {
            title: 'Người duyệt',
            render: (_, l) =>
              l.decidedByName ? (
                <Tooltip title={l.decisionNote}>
                  <span>{l.decidedByName}</span>
                </Tooltip>
              ) : (
                ''
              ),
          },
          {
            title: '',
            render: (_, l) =>
              l.status === 'PENDING' && (
                <Space>
                  <Button size="small" type="primary" onClick={() => setDeciding({ leave: l, approve: true })}>Duyệt</Button>
                  <Button size="small" danger onClick={() => setDeciding({ leave: l, approve: false })}>Từ chối</Button>
                </Space>
              ),
          },
        ]}
      />

      <Modal
        title={deciding ? `${deciding.approve ? 'Duyệt' : 'Từ chối'} đơn nghỉ: ${deciding.leave.employeeName}` : ''}
        open={deciding !== null}
        onCancel={() => setDeciding(null)}
        footer={null}
        destroyOnHidden
      >
        {deciding && (
          <Form layout="vertical" onFinish={(v: { note?: string }) => decide.mutate(v.note)}>
            <Typography.Paragraph>
              {leaveDays(deciding.leave)}, {leaveTypeLabel[deciding.leave.type].toLowerCase()}. Lý do: {deciding.leave.reason}
            </Typography.Paragraph>
            {deciding.approve && (
              <Typography.Paragraph type="secondary">Các ca đã xếp cho người này trong những ngày đó sẽ bị gỡ.</Typography.Paragraph>
            )}
            <Form.Item
              name="note"
              label={deciding.approve ? 'Ghi chú' : 'Lý do từ chối'}
              rules={[{ required: !deciding.approve, message: 'Cần ghi lý do' }, { max: 300 }]}
            >
              <Input.TextArea rows={2} />
            </Form.Item>
            <Button type="primary" danger={!deciding.approve} htmlType="submit" block loading={decide.isPending}>
              {deciding.approve ? 'Duyệt' : 'Từ chối'}
            </Button>
          </Form>
        )}
      </Modal>
    </>
  )
}
