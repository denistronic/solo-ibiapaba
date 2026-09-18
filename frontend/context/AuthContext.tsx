"use client";

import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import {
  login as loginRequest,
  getCurrentUser,
  type AuthUser,
} from "@/lib/auth";
import { getToken, saveToken, clearToken } from "@/lib/token";

type AuthContextValue = {
  user: AuthUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = getToken();

    if (!token) {
      setLoading(false);
      return;
    }

    getCurrentUser()
      .then(setUser)
      .catch(() => {
        clearToken();
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  async function login(email: string, password: string) {
    const response = await loginRequest(email, password);

    saveToken(response.token);

    setUser({
      id: "",
      name: response.name,
      email: response.email,
      role: response.role,
      active: true,
    });

    try {
      const fullUser = await getCurrentUser();
      setUser(fullUser);
    } catch {
      // token acabou de ser emitido; se /me falhar aqui é algo mais sério,
      // mas não vale travar o login por causa disso
    }
  }

  function logout() {
    clearToken();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth precisa ser usado dentro de um AuthProvider");
  }

  return context;
}