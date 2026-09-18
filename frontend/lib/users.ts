import { request } from "@/lib/api";
import type { UserRole } from "@/lib/auth";

export type ManagedUser = {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  active: boolean;
};

export function listUsers() {
  return request<ManagedUser[]>("/users");
}

export function createUser(payload: {
  name: string;
  email: string;
  password: string;
  role: UserRole;
}) {
  return request<ManagedUser>("/users", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateUser(
  id: string,
  payload: { name: string; email: string; role: UserRole },
) {
  return request<ManagedUser>(`/users/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function updateUserStatus(id: string, active: boolean) {
  return request<ManagedUser>(`/users/${id}/status`, {
    method: "PUT",
    body: JSON.stringify({ active }),
  });
}
