import axios, { AxiosError } from 'axios'

const TOKEN_KEY = 'bnn.token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // Private mode without storage: the session simply lasts until reload.
  }
}

export const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token && !config.url?.startsWith('/public')) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    // Expired token or locked account (BR-03): back to the login page.
    const url = error.config?.url ?? ''
    if (error.response?.status === 401 && !url.startsWith('/auth/login') && !url.startsWith('/public')) {
      setToken(null)
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    return Promise.reject(error)
  },
)

/** Human-readable message from a Problem Details response. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { detail?: string } | undefined
    if (data?.detail) return data.detail
    if (!error.response) return 'Không kết nối được máy chủ'
  }
  return 'Có lỗi xảy ra, vui lòng thử lại'
}
