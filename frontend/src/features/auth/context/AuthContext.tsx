import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, getToken, setToken } from '@/shared/api/client'
import type { Employee, LoginResponse } from '@/shared/api/types'

interface AuthState {
  user: Employee | null
  token: string | null
  loading: boolean
  login: (username: string, password: string) => Promise<Employee>
  logout: () => void
  /** BR-41: after a password change, this device goes on with the new token. */
  replaceToken: (token: string) => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(getToken())
  const [user, setUser] = useState<Employee | null>(null)
  const [loading, setLoading] = useState<boolean>(token !== null)

  // Restore the session after a reload.
  useEffect(() => {
    if (!token || user) return
    api
      .get<Employee>('/auth/me')
      .then((res) => setUser(res.data))
      .catch(() => {
        setToken(null)
        setTokenState(null)
      })
      .finally(() => setLoading(false))
  }, [token, user])

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.post<LoginResponse>('/auth/login', { username, password })
    setToken(res.data.token)
    setTokenState(res.data.token)
    setUser(res.data.user)
    return res.data.user
  }, [])

  const logout = useCallback(() => {
    setToken(null)
    setTokenState(null)
    setUser(null)
  }, [])

  const replaceToken = useCallback((next: string) => {
    setToken(next)
    setTokenState(next)
  }, [])

  const value = useMemo(
    () => ({ user, token, loading, login, logout, replaceToken }),
    [user, token, loading, login, logout, replaceToken],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
