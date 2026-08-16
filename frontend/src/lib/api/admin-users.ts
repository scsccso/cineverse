import { apiFetch } from "./client";
import type { Page, Role, UserResponse } from "./types";

export interface UpdateUserRoleRequest {
  role: Role;
}

/** email is an optional case-insensitive "contains" search, added alongside
 * page/size rather than as a fourth positional param so existing callers
 * that only pass page/size don't need updating. */
export async function getAdminUsers(
  token: string,
  page: number = 0,
  size: number = 20,
  email?: string,
): Promise<Page<UserResponse>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (email) query.set("email", email);
  return apiFetch<Page<UserResponse>>(`/api/v1/admin/users?${query.toString()}`, {
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
