import { request } from "@/lib/api";

export type UserRole = "USER" | "ADMIN";

export type AuthUser = {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  active: boolean;
};

export type LoginResponse = {
  token: string;
  name: string;
  email: string;
  role: UserRole;
};

export function login(email: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>("/auth/login", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: email.trim().toLowerCase(), password }),
  }, false);
}

export function getCurrentUser(): Promise<AuthUser> {
  return request<AuthUser>("/auth/me");
}
