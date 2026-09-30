import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { ItemStatus } from '@/shared/api/types'
import { itemStatusLabel } from '@/shared/utils/format'
import StatusTag from './StatusTag'

describe('StatusTag', () => {
  it('shows the Vietnamese name of every dish status', () => {
    for (const [status, label] of Object.entries(itemStatusLabel)) {
      const { unmount } = render(<StatusTag status={status as ItemStatus} />)
      expect(screen.getByText(label)).toBeTruthy()
      unmount()
    }
  })
})
