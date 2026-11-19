"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { api, type Me } from "@/lib/api";

type AuthState = {
  me: Me | null;
  loading: boolean;
  refresh: () => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      setMe(await api<Me>("GET", "/api/auth/me"));
    } catch {
      setMe(null);
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    await api("POST", "/api/auth/logout");
    setMe(null);
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return (
    <AuthContext.Provider value={{ me, loading, refresh, logout }}>{children}</AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("AuthProvider 안에서만 쓸 수 있습니다");
  return ctx;
}
