import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { Result, Spin } from 'antd'
import type { Role } from '@/shared/api/types'
import { hasRole } from '@/shared/utils/format'
import { useAuth } from '../context/AuthContext'

/** Hides screens a role may not use. The backend still checks every call (BR-02). */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <Spin fullscreen />
  if (!user) return <Navigate to="/login" replace />
  if (!roles.some((r) => hasRole(user.role, r))) {
    return <Result status="403" title="Không có quyền" subTitle="Tài khoản của bạn không dùng được màn hình này." />
  }
  return <>{children}</>
}
