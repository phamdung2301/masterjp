import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { TOKEN_KEY } from '../api/client.js';
import { fetchMe, login as apiLogin, register as apiRegister } from '../api/auth.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      setLoading(false);
      return;
    }
    fetchMe()
      .then(setUser)
      .catch(() => localStorage.removeItem(TOKEN_KEY))
      .finally(() => setLoading(false));
  }, []);

  // ⚠️ Backend trả { accessToken, user } — destructure ĐÚNG accessToken.
  const handleLogin = useCallback(async (email, password) => {
    const { accessToken, user: u } = await apiLogin(email, password);
    localStorage.setItem(TOKEN_KEY, accessToken);
    setUser(u);
    return u;
  }, []);

  const handleRegister = useCallback(async (payload) => {
    const { accessToken, user: u } = await apiRegister(payload);
    localStorage.setItem(TOKEN_KEY, accessToken);
    setUser(u);
    return u;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const u = await fetchMe();
      setUser(u);
      return u;
    } catch {
      return null;
    }
  }, []);

  const canSharePublic = user && (user.role === 'ADMIN' || user.plan === 'PREMIUM');

  return (
    <AuthContext.Provider
      value={{ user, loading, isLoggedIn: !!user, canSharePublic, login: handleLogin, register: handleRegister, logout, refreshUser }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
