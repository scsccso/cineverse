import { apiFetch } from "./client";
import type { Page, Role, UserResponse } from "./types";

export interface UpdateUserRoleRequest {
  role: Role;
}

export async function getAdminUsers(token: string, page: number = 0, size: number = 20): Promise<Page<UserResponse>> {
  return apiFetch<Page<UserResponse>>(`/api/v1/admin/users?page=${page}&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function updateUserRole(token: string, id: string, request: UpdateUserRoleRequest): Promise<UserResponse> {
  return apiFetch<UserResponse>(`/api/v1/admin/users/${id}/role`, {
    method: "PATCH",
    body: request,
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function deleteUser(token: string, id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/admin/users/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
}
