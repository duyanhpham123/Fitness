import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, setUnauthorizedHandler, tokens } from '../api/client.js';

const AuthContext = createContext(null);

// Thứ tự ưu tiên khi chọn trang chủ cho tài khoản có nhiều vai trò
const HOME_BY_ROLE = [
  ['ADMIN', '/admin'],
  ['STAFF', '/admin'],
  ['TRAINER', '/trainer'],
  ['MEMBER', '/member'],
];

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const clearSession = useCallback(() => {
    tokens.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    if (!tokens.access) {
      setLoading(false);
      return;
    }
    api('/api/users/me')
      .then(setUser)
      .catch(clearSession)
      .finally(() => setLoading(false));
  }, [clearSession]);

  const startSession = useCallback((authResponse) => {
    tokens.save(authResponse);
    setUser(authResponse.user);
    return authResponse.user;
  }, []);

  const value = useMemo(() => {
    const roles = user?.roles || [];
    return {
      user,
      loading,
      roles,
      hasRole: (...wanted) => wanted.some((r) => roles.includes(r)),
      homePath: () => HOME_BY_ROLE.find(([r]) => roles.includes(r))?.[1] || '/login',
      async login(email, password, rememberMe) {
        const res = await api('/api/auth/login', { method: 'POST', body: { email, password, rememberMe }, auth: false });
        return startSession(res);
      },
      async verifyEmail(email, otp) {
        const res = await api('/api/auth/verify-email', { method: 'POST', body: { email, otp }, auth: false });
        return startSession(res);
      },
      async logout() {
        const refreshToken = tokens.refresh;
        if (refreshToken) {
          // Gửi kèm access token để backend chặn luôn token này; lỗi mạng cũng vẫn đăng xuất ở máy
          await api('/api/auth/logout', { method: 'POST', body: { refreshToken } }).catch(() => {});
        }
        clearSession();
      },
      async reloadUser() {
        setUser(await api('/api/users/me'));
      },
      clearSession,
    };
  }, [user, loading, startSession, clearSession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
