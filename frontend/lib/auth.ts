import { API_URL, ApiError } from "@/lib/api";
import { getToken } from "@/lib/token";

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

export async function login(email: string, password: string): Promise<LoginResponse> {
  const response = await fetch(`${API_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    throw await parseAuthError(response);
  }

  return (await response.json()) as LoginResponse;
}

export async function getCurrentUser(): Promise<AuthUser> {
  const token = getToken();

  const response = await fetch(`${API_URL}/auth/me`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (!response.ok) {
    throw new ApiError("Sessão expirada", response.status);
  }

  return (await response.json()) as AuthUser;
}

async function parseAuthError(response: Response): Promise<ApiError> {
  let message = "Não foi possível entrar";

  try {
    const payload = await response.json();
    message = payload.message ?? message;
  } catch {
    // resposta sem corpo JSON
  }

  return new ApiError(message, response.status);
}