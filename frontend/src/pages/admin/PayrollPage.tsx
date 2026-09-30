import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Card, Col, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Row, Space, Table, Tag, Typography } from 'antd'
import { DeleteOutlined, DownloadOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api, errorMessage } from '../../api/client'
import type { PayrollDetail, PayrollStatus, PayrollSummary, Payslip } from '../../api/types'
import { money, moneyInputProps, payText, roleLabel } from '../../utils/format'
import { duration, payrollSheet, payrollStatusLabel, periodLabel, utf16leWithBom } from '../../utils/hr'

const statusTag = (status: PayrollStatus) => <Tag color={status === 'FINALIZED' ? 'green' : 'gold'}>{payrollStatusLabel[status]}</Tag>

function signedMoney(amount: number) {
  return (
    <Typography.Text type={amount < 0 ? 'danger' : 'success'}>
      {amount > 0 ? '+' : ''}
      {money(amount)}
    </Typography.Text>
  )
}

function download(fileName: string, text: string) {
  const url = URL.createObjectURL(new Blob([utf16leWithBom(text)], { type: 'text/csv' }))
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

/** FR-15: monthly payroll from attendance and paid leave; finalizing locks the month (BR-26, BR-27). */
export default function PayrollPage() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [adjusting, setAdjusting] = useState<Payslip | null>(null)

  const payrolls = useQuery({ queryKey: ['payrolls'], queryFn: () => api.get<PayrollSummary[]>('/payrolls').then((r) => r.data) })
  const currentId = selectedId ?? payrolls.data?.[0]?.id ?? null
  const detail = useQuery({
    queryKey: ['payroll', currentId],
    queryFn: () => api.get<PayrollDetail>(`/payrolls/${currentId}`).then((r) => r.data),
    enabled: currentId !== null,
  })

  const onError = (e: unknown) => message.error(errorMessage(e))
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['payrolls'] })
    queryClient.invalidateQueries({ queryKey: ['payroll'] })
  }

  const create = useMutation({
    mutationFn: (v: { month: Dayjs; standardDays: number }) =>
      api.post<PayrollDetail>('/payrolls', { period: v.month.format('YYYY-MM'), standardDays: v.standardDays }).then((r) => r.data),
    onSuccess: (p) => {
      setCreating(false)
      setSelectedId(p.id)
      message.success('Đã tạo và tính bảng lương')
      refresh()
    },
    onError,
  })
  const recalculate = useMutation({
    mutationFn: () => api.post(`/payrolls/${currentId}/recalculate`),
    onSuccess: () => {
      message.success('Đã tính lại')
      refresh()
    },
    onError,
  })
  const finalize = useMutation({
    mutationFn: () => api.post(`/payrolls/${currentId}/finalize`),
    onSuccess: () => {
      message.success('Đã chốt bảng lương')
      refresh()
    },
    onError,
  })
  const addAdjustment = useMutation({
    mutationFn: (v: { amount: number; reason: string }) => api.post(`/payslips/${adjusting!.id}/adjustments`, v),
    onSuccess: () => {
      setAdjusting(null)
      refresh()
    },
    onError,
  })
  const removeAdjustment = useMutation({ mutationFn: (id: number) => api.delete(`/pay-adjustments/${id}`), onSuccess: refresh, onError })

  const p = detail.data
  const draft = p?.status === 'DRAFT'

  return (
    <>
      <div className="page-title">
        <Typography.Title level={3}>Bảng lương</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
          Tạo bảng lương
        </Button>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={7}>
          <Card title="Các tháng" size="small">
            <Table<PayrollSummary>
              size="small"
              rowKey="id"
              pagination={false}
              loading={payrolls.isLoading}
              dataSource={payrolls.data ?? []}
              locale={{ emptyText: 'Chưa có bảng lương' }}
              onRow={(r) => ({ onClick: () => setSelectedId(r.id), style: { cursor: 'pointer' } })}
              rowClassName={(r) => (r.id === currentId ? 'ant-table-row-selected' : '')}
              columns={[
                { title: 'Tháng', render: (_, r) => periodLabel(r.period) },
                { title: '', render: (_, r) => statusTag(r.status) },
                { title: 'Thực nhận', render: (_, r) => money(r.totalNet) },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={17}>
          {p ? (
            <Card
              size="small"
              loading={detail.isLoading}
              title={
                <Space>
                  Tháng {periodLabel(p.period)} {statusTag(p.status)}
                </Space>
              }
              extra={
                <Space wrap>
                  {draft && (
                    <Button icon={<ReloadOutlined />} loading={recalculate.isPending} onClick={() => recalculate.mutate()}>
                      Tính lại
                    </Button>
                  )}
                  {draft && (
                    <Popconfirm
                      title="Chốt bảng lương?"
                      description="Sau khi chốt không sửa được bảng lương, chấm công và nghỉ phép của tháng này."
                      onConfirm={() => finalize.mutate()}
                    >
                      <Button type="primary" loading={finalize.isPending}>Chốt</Button>
                    </Popconfirm>
                  )}
                  <Button icon={<DownloadOutlined />} onClick={() => download(`bang-luong-${p.period}.csv`, payrollSheet(p))}>
                    Xuất Excel
                  </Button>
                </Space>
              }
            >
              <Typography.Paragraph type="secondary">
                {p.payslipCount} người, công chuẩn {p.standardDays} ngày, tổng thực nhận {money(p.totalNet)}.
                {p.finalizedAt && ` Chốt bởi ${p.finalizedByName ?? ''} lúc ${dayjs(p.finalizedAt).format('HH:mm DD/MM/YYYY')}.`}
              </Typography.Paragraph>
              <Table<Payslip>
                size="small"
                rowKey="id"
                pagination={false}
                dataSource={p.payslips}
                scroll={{ x: true }}
                expandable={{
                  rowExpandable: (s) => s.adjustments.length > 0,
                  expandedRowRender: (s) => (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      {s.adjustments.map((a) => (
                        <Space key={a.id}>
                          {signedMoney(a.amount)}
                          <Typography.Text>{a.reason}</Typography.Text>
                          {draft && (
                            <Button size="small" type="text" danger icon={<DeleteOutlined />} title="Xoá khoản này" onClick={() => removeAdjustment.mutate(a.id)} />
                          )}
                        </Space>
                      ))}
                    </div>
                  ),
                }}
                columns={[
                  {
                    title: 'Nhân viên',
                    render: (_, s) => (
                      <>
                        <div>{s.employeeName}</div>
                        <Typography.Text type="secondary">{roleLabel[s.role]}</Typography.Text>
                      </>
                    ),
                  },
                  { title: 'Mức lương', render: (_, s) => payText(s.payType, s.payRate) },
                  {
                    title: 'Công',
                    render: (_, s) =>
                      s.payType === 'HOURLY'
                        ? duration(s.workedMinutes)
                        : `${s.workDays} ngày${s.paidLeaveDays ? ` + ${s.paidLeaveDays} ngày nghỉ có lương` : ''}`,
                  },
                  { title: 'Lương theo công', render: (_, s) => money(s.baseAmount) },
                  { title: 'Thưởng, phạt', render: (_, s) => (s.adjustmentAmount === 0 ? '' : signedMoney(s.adjustmentAmount)) },
                  { title: 'Thực nhận', render: (_, s) => <strong>{money(s.netAmount)}</strong> },
                  { title: '', render: (_, s) => draft && <Button size="small" onClick={() => setAdjusting(s)}>Thưởng, phạt</Button> },
                ]}
              />
            </Card>
          ) : (
            <Card size="small" loading={payrolls.isLoading || detail.isLoading}>
              Chọn hoặc tạo một bảng lương.
            </Card>
          )}
        </Col>
      </Row>

      <Modal title="Tạo bảng lương" open={creating} onCancel={() => setCreating(false)} footer={null} destroyOnHidden>
        <Form layout="vertical" initialValues={{ month: dayjs().subtract(1, 'month'), standardDays: 26 }} onFinish={(v) => create.mutate(v)}>
          <Form.Item name="month" label="Tháng" rules={[{ required: true }]}>
            <DatePicker picker="month" format="MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="standardDays" label="Số ngày công chuẩn (cho lương tháng)" rules={[{ required: true }]}>
            <InputNumber min={1} max={31} style={{ width: '100%' }} />
          </Form.Item>
          <Typography.Paragraph type="secondary">
            Tính từ các lượt chấm công đã ra ca và ngày nghỉ có lương đã duyệt. Tháng chưa kết thúc thì chỉ lưu nháp, chưa chốt được.
          </Typography.Paragraph>
          <Button type="primary" htmlType="submit" block loading={create.isPending}>Tạo và tính</Button>
        </Form>
      </Modal>

      <Modal title={`Thưởng, phạt: ${adjusting?.employeeName ?? ''}`} open={adjusting !== null} onCancel={() => setAdjusting(null)} footer={null} destroyOnHidden>
        <Form layout="vertical" onFinish={(v) => addAdjustment.mutate(v)}>
          <Form.Item name="amount" label="Số tiền (số âm là phạt)" rules={[{ required: true }]}>
            <InputNumber step={10000} style={{ width: '100%' }} {...moneyInputProps} />
          </Form.Item>
          <Form.Item name="reason" label="Lý do" rules={[{ required: true, max: 300 }]}>
            <Input placeholder="Thưởng chuyên cần, đi muộn nhiều lần..." />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={addAdjustment.isPending}>Lưu</Button>
        </Form>
      </Modal>
    </>
  )
}
