"use client";

import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/lib/auth/auth-context";
import { getAdminUsers, updateUserRole, deleteUser } from "@/lib/api/admin-users";
import type { Page, UserResponse } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

export default function AdminUsersPage() {
  const { callAuthorized, user: currentUser } = useAuth();
  const [usersPage, setUsersPage] = useState<Page<UserResponse> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  
  // Loading derived from data state, matching dashboard/page.tsx pattern
  // This avoids set-state-in-effect issues and stale-data flashes.
  const loading = !usersPage || usersPage.number !== page;

  // Dialog states
  const [confirmDialog, setConfirmDialog] = useState<{
    isOpen: boolean;
    title: string;
    description: string;
    action: () => Promise<void>;
  }>({
    isOpen: false,
    title: "",
    description: "",
    action: async () => {},
  });
  const [actionError, setActionError] = useState<string | null>(null);

  // Tracks the in-flight request so we can ignore stale responses after
  // a rapid page change — same cancellation pattern used in dashboard/page.tsx.
  const requestIdRef = useRef(0);

  useEffect(() => {
    const thisRequestId = ++requestIdRef.current;
    callAuthorized((token) => getAdminUsers(token, page))
      .then((data) => {
        if (requestIdRef.current !== thisRequestId) return;
        setUsersPage(data);
        setError(null);
      })
      .catch(() => {
        if (requestIdRef.current !== thisRequestId) return;
        setUsersPage(null);
        setError("加载用户列表失败");
      });
  }, [callAuthorized, page]);

  const handleRoleToggle = (user: UserResponse) => {
    const newRole = user.role === "ADMIN" ? "CUSTOMER" : "ADMIN";
    setConfirmDialog({
      isOpen: true,
      title: "更改用户角色",
      description: `确定要将 ${user.email} 的角色更改为 ${newRole} 吗？`,
      action: async () => {
        try {
          await callAuthorized((token) => updateUserRole(token, user.id, { role: newRole }));
          setUsersPage((prev) => {
            if (!prev) return prev;
            return {
              ...prev,
              content: prev.content.map((u) => (u.id === user.id ? { ...u, role: newRole } : u)),
            };
          });
          closeDialog();
        } catch {
          setActionError("角色更新失败，请重试");
        }
      },
    });
  };

  const handleDelete = (user: UserResponse) => {
    setConfirmDialog({
      isOpen: true,
      title: "删除用户",
      description: `确定要删除用户 ${user.email} 吗？此操作不可逆。`,
      action: async () => {
        try {
          await callAuthorized((token) => deleteUser(token, user.id));
          setUsersPage((prev) => {
            if (!prev) return prev;
            return {
              ...prev,
              content: prev.content.filter((u) => u.id !== user.id),
            };
          });
          closeDialog();
        } catch (err: unknown) {
          if (err instanceof ApiError && err.status === 409) {
            setActionError("无法删除：该用户拥有订单记录");
          } else {
            setActionError("删除失败，请重试");
          }
        }
      },
    });
  };

  const closeDialog = () => {
    setConfirmDialog((prev) => ({ ...prev, isOpen: false }));
    setActionError(null);
  };

  return (
    <section className="mx-auto max-w-6xl px-6 py-10">
      <header className="mb-6">
        <h1 className="font-heading text-2xl font-semibold text-foreground">管理后台 · 用户管理</h1>
        <p className="mt-1 text-sm text-muted-foreground">管理平台用户与权限</p>
      </header>

      {error && (
        <div role="alert" className="mb-6 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>用户列表</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-8 text-center text-sm text-muted-foreground">加载中...</div>
          ) : !usersPage || usersPage.content.length === 0 ? (
            <div className="py-8 text-center text-sm text-muted-foreground">暂无用户数据</div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <caption className="sr-only">用户列表</caption>
                <thead>
                  <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
                    <th scope="col" className="px-3 py-2 font-medium">邮箱</th>
                    <th scope="col" className="px-3 py-2 font-medium">姓名</th>
                    <th scope="col" className="px-3 py-2 font-medium">角色</th>
                    <th scope="col" className="px-3 py-2 font-medium">注册时间</th>
                    <th scope="col" className="px-3 py-2 font-medium text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {usersPage.content.map((user) => (
                    <tr key={user.id} className="border-b border-border last:border-0">
                      <td className="px-3 py-2 text-foreground">{user.email}</td>
                      <td className="px-3 py-2 text-foreground">{user.fullName || "-"}</td>
                      <td className="px-3 py-2">
                        <Badge variant={user.role === "ADMIN" ? "default" : "secondary"}>{user.role}</Badge>
                      </td>
                      <td className="px-3 py-2 font-mono text-foreground">
                        {user.createdAt ? new Date(user.createdAt).toLocaleDateString() : "-"}
                      </td>
                      <td className="px-3 py-2 text-right">
                        <div className="flex justify-end gap-2">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            disabled={user.id === currentUser?.id}
                            onClick={() => handleRoleToggle(user)}
                          >
                            切换角色
                          </Button>
                          <Button
                            type="button"
                            variant="destructive"
                            size="sm"
                            disabled={user.id === currentUser?.id}
                            onClick={() => handleDelete(user)}
                          >
                            删除
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination controls */}
          {usersPage && usersPage.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between">
              <p className="text-sm text-muted-foreground">
                共 {usersPage.totalElements} 条，第 {usersPage.number + 1} / {usersPage.totalPages} 页
              </p>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={usersPage.first || loading}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  上一页
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={usersPage.last || loading}
                  onClick={() => setPage((p) => p + 1)}
                >
                  下一页
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Confirmation Dialog Overlay */}
      {confirmDialog.isOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirm-dialog-title"
        >
          <Card className="w-full max-w-md shadow-lg">
            <CardHeader>
              <CardTitle id="confirm-dialog-title">{confirmDialog.title}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">{confirmDialog.description}</p>

              {actionError && (
                <div role="alert" className="mt-4 rounded border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                  {actionError}
                </div>
              )}

              <div className="mt-6 flex justify-end gap-3">
                <Button type="button" variant="outline" onClick={closeDialog}>
                  取消
                </Button>
                <Button type="button" variant="default" onClick={confirmDialog.action}>
                  确认
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </section>
  );
}
