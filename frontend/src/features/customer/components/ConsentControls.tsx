import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { App, Button, Flex, Input, Segmented, Tag, Typography } from 'antd'
import { api, errorMessage } from '@/shared/api/client'
import type { ConsentChannel, Customer } from '@/shared/api/types'
import { time } from '@/shared/utils/format'
import { channelLabel, consentColor, consentLabel, consentState } from '../utils/customer'

interface Props {
  customer: Customer
  onChanged: (customer: Customer) => void
}

/** FR-19.4, BR-44: what the guest agreed to hear, and recording a new agreement or a refusal. */
export default function ConsentControls({ customer, onChanged }: Props) {
  const { message } = App.useApp()
  const [channel, setChannel] = useState<ConsentChannel>(customer.consentChannel ?? 'ZALO')
  const [source, setSource] = useState('Hỏi tại quầy')
  const state = consentState(customer)
  const onError = (e: unknown) => message.error(errorMessage(e))

  const consent = useMutation({
    mutationFn: () => api.post<Customer>(`/customers/${customer.id}/consent`, { channel, source: source.trim() }).then((r) => r.data),
    onSuccess: (c) => {
      message.success('Đã ghi khách đồng ý nhận tin')
      onChanged(c)
    },
    onError,
  })
  const optOut = useMutation({
    mutationFn: () => api.post<Customer>(`/customers/${customer.id}/opt-out`).then((r) => r.data),
    onSuccess: (c) => {
      message.success('Đã ghi khách từ chối nhận tin')
      onChanged(c)
    },
    onError,
  })

  return (
    <Flex vertical gap={8}>
      <div>
        <Tag color={consentColor[state]}>{consentLabel[state]}</Tag>
        <Typography.Text type="secondary">
          {state === 'AGREED' && customer.consentChannel
            ? `${channelLabel[customer.consentChannel]}, ${time(customer.consentAt)}, ${customer.consentSource}`
            : state === 'REFUSED'
              ? `từ ${time(customer.optedOutAt)}`
              : ''}
        </Typography.Text>
      </div>
      <Flex gap={8} wrap align="center">
        <Segmented<ConsentChannel>
          value={channel}
          onChange={setChannel}
          options={[
            { value: 'ZALO', label: channelLabel.ZALO },
            { value: 'SMS', label: channelLabel.SMS },
          ]}
        />
        <Input
          style={{ flex: 1, minWidth: 160 }}
          value={source}
          maxLength={100}
          placeholder="Cách thu thập, ví dụ: hỏi tại quầy"
          aria-label="Cách thu thập"
          onChange={(e) => setSource(e.target.value)}
        />
      </Flex>
      <Flex gap={8} wrap>
        <Button type="primary" disabled={!source.trim()} loading={consent.isPending} onClick={() => consent.mutate()}>
          Ghi đồng ý
        </Button>
        <Button danger disabled={state === 'REFUSED'} loading={optOut.isPending} onClick={() => optOut.mutate()}>
          Ghi từ chối
        </Button>
      </Flex>
    </Flex>
  )
}
