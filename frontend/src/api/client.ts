/** Gọi backend: mọi phản hồi có dạng { success, data, error } (docs/backend/API_CONVENTIONS.md). */

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: { field: string; message: string }[];
}

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details: ApiErrorBody['details'] = []) {
    super(message);
  }
}

const TOKEN_KEY = 'rms.token';

export const tokenStore = {
  get: () => {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (t: string | null) => {
    try {
      if (t) localStorage.setItem(TOKEN_KEY, t);
      else localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* trình duyệt chặn storage: phiên chỉ sống trong bộ nhớ */
    }
  },
};

/** AuthProvider đăng ký: 401 → đăng xuất, 403 PASSWORD_CHANGE_REQUIRED → màn đổi mật khẩu. */
let onAuthProblem: (e: ApiError) => void = () => {};
export function setAuthProblemHandler(fn: (e: ApiError) => void) {
  onAuthProblem = fn;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res: Response;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.');
  }
  const json = await res.json().catch(() => null);
  if (res.ok && json?.success) return json.data as T;

  const err = json?.error as ApiErrorBody | undefined;
  const e = new ApiError(res.status, err?.code ?? 'HTTP_' + res.status, err?.message ?? `Lỗi máy chủ (${res.status})`, err?.details);
  if (res.status === 401 || e.code === 'PASSWORD_CHANGE_REQUIRED') onAuthProblem(e);
  throw e;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
};

/** Khóa idempotency: sinh một lần cho mỗi lần bấm, gửi lại đúng khóa khi thử lại. */
export function newRequestKey(): string {
  return crypto.randomUUID();
}
