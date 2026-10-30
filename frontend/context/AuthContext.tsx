"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { login as loginRequest, getCurrentUser, type AuthUser } from "@/lib/auth";
import { SESSION_EXPIRED } from "@/lib/api";
import { getToken, saveToken, clearToken } from "@/lib/token";

type AuthContextValue = {
  user: AuthUser | null; loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void; refreshUser: () => Promise<AuthUser>;
};
const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const version = useRef(0);

  const logout = useCallback(() => {
    version.current++;
    clearToken(); setUser(null); setLoading(false);
  }, []);

  const refreshUser = useCallback(async () => {
    const current = version.current;
    const full = await getCurrentUser();
    if (current === version.current) setUser(full);
    return full;
  }, []);

  useEffect(() => {
    let disposed = false;
    const restore = () => {
      const current = ++version.current;
      const lookup = async () => getToken() ? getCurrentUser() : null;
      void lookup().then(full => { if (!disposed && current === version.current) setUser(full); })
        .catch(() => { if (!disposed && current === version.current) setUser(null); })
        .finally(() => { if (!disposed && current === version.current) setLoading(false); });
    };
    restore();
    const expired = () => { version.current++; setUser(null); setLoading(false); };
    const changed = (event: StorageEvent) => { if (event.key === "solo-ibiapaba:token" || event.key === null) restore(); };
    window.addEventListener(SESSION_EXPIRED, expired);
    window.addEventListener("storage", changed);
    return () => {
      disposed = true;
      window.removeEventListener(SESSION_EXPIRED, expired);
      window.removeEventListener("storage", changed);
    };
  }, []);

  async function login(email: string, password: string) {
    const current = ++version.current;
    const response = await loginRequest(email, password);
    if (current !== version.current) return;
    saveToken(response.token);
    const full = await getCurrentUser();
    if (current === version.current) { setUser(full); setLoading(false); }
  }

  return <AuthContext.Provider value={{ user, loading, login, logout, refreshUser }}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth precisa ser usado dentro de um AuthProvider");
  return context;
}
