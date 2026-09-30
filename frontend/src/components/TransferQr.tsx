import { Flex, Spin, Typography } from 'antd'
import type { PaymentInstruction } from '../api/types'
import { money } from '../utils/format'

/** VietQR for one payment. The reference in the transfer content is how the webhook finds the bill (BR-15). */
export default function TransferQr({ instruction }: { instruction: PaymentInstruction }) {
  return (
    <Flex vertical align="center" gap={4}>
      <img
        src={instruction.qrImageUrl}
        alt={`Mã VietQR ${money(instruction.amount)}`}
        width={260}
        style={{ maxWidth: '100%' }}
      />
      <Typography.Title level={4} style={{ margin: 0 }}>
        {money(instruction.amount)}
      </Typography.Title>
      <Typography.Text>
        Nội dung: <Typography.Text strong copyable>{instruction.reference}</Typography.Text>
      </Typography.Text>
      <Typography.Text type="secondary">
        {instruction.bankAccountName} · {instruction.bankAccountNo}
      </Typography.Text>
      <Typography.Text type="secondary" style={{ textAlign: 'center' }}>
        Quét bằng app ngân hàng, giữ nguyên số tiền và nội dung.
      </Typography.Text>
      <Flex align="center" gap={8} style={{ marginTop: 8 }}>
        <Spin size="small" />
        <Typography.Text>Đang chờ tiền về, màn hình sẽ tự cập nhật…</Typography.Text>
      </Flex>
    </Flex>
  )
}
