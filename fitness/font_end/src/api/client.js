// Gọi REST API của backend. Access token hết hạn (401 TOKEN_EXPIRED) thì tự refresh rồi gọi lại 1 lần.
// Refresh token chỉ dùng được 1 lần (backend xoay vòng), nên nhiều request cùng hết hạn
// phải dùng chung 1 lần refresh (biến `refreshing`).

const BASE = import.meta.env.VITE_API_URL || '';
const ACCESS_KEY = 'fitness.accessToken';
const REFRESH_KEY = 'fitness.refreshToken';

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || 'Có lỗi xảy ra, vui lòng thử lại');
    this.status = status;
    this.code = body?.code;
    this.errors = body?.errors || {};
  }
}

export const tokens = {
  get access() {
    return localStorage.getItem(ACCESS_KEY);
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY);
  },
  save({ accessToken, refreshToken }) {
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

let onUnauthorized = () => {};
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

async function readBody(res) {
  if (res.status === 204) return null;
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

let refreshing = null;
function refreshTokens() {
  if (!refreshing) {
    refreshing = (async () => {
      const res = await fetch(`${BASE}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: tokens.refresh }),
      });
      const body = await readBody(res);
      if (!res.ok) {
        tokens.clear();
        throw new ApiError(res.status, body);
      }
      tokens.save(body);
      return body;
    })().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

/**
 * @param {string} path vd. '/api/packages'
 * @param {{method?: string, body?: any, query?: object, headers?: object, auth?: boolean}} options
 */
export async function api(path, { method = 'GET', body, query, headers = {}, auth = true, retried = false } = {}) {
  const h = { ...headers };
  if (body !== undefined) h['Content-Type'] = 'application/json';
  if (auth && tokens.access) h.Authorization = `Bearer ${tokens.access}`;

  const res = await fetch(`${BASE}${path}${toQuery(query)}`, {
    method,
    headers: h,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const data = await readBody(res);

  if (res.status === 401 && auth && !retried && data?.code === 'TOKEN_EXPIRED' && tokens.refresh) {
    try {
      await refreshTokens();
    } catch (e) {
      onUnauthorized();
      throw e;
    }
    return api(path, { method, body, query, headers, auth, retried: true });
  }
  if (!res.ok) {
    if (res.status === 401 && auth) onUnauthorized();
    throw new ApiError(res.status, data);
  }
  return data;
}

function toQuery(query) {
  if (!query) return '';
  const params = new URLSearchParams();
  Object.entries(query).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') params.append(k, v);
  });
  const s = params.toString();
  return s ? `?${s}` : '';
}

// Khóa chống gửi trùng cho mua gói / đặt lịch (backend trả lại kết quả cũ nếu gửi lại cùng khóa)
export function newIdempotencyKey() {
  return crypto.randomUUID();
}
