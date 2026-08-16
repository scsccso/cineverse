"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { Search } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { getAdminUsers, updateUserRole, deleteUser } from "@/lib/api/admin-users";
import type { Page, UserResponse } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

export default function AdminUsersPage() {
  const { callAuthorized, user: currentUser } = useAuth();
  const [emailInput, setEmailInput] = useState("");
  const [appliedEmail, setAppliedEmail] = useState("");
  const [page, setPage] = useState(0);

  // Loading derived from data state, matching dashboard/page.tsx pattern —
  // extended to also track the search term that produced the data on hand,
  // same reasoning as admin/movies/page.tsx's title search.
  const [result, setResult] = useState<{ page: number; email: string; data: Page<UserResponse> } | null>(null);
  const loading = !result || result.page !== page || result.email !== appliedEmail;

  const [error, setError] = useState<string | null>(null);

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
    callAuthorized((token) => getAdminUsers(token, page, 20, appliedEmail || undefined))
      .then((data) => {
        if (requestIdRef.current !== thisRequestId) return;
        setResult({ page, email: appliedEmail, data });
        setError(null);
      })
      .catch(() => {
        if (requestIdRef.current !== thisRequestId) return;
        setError("Failed to load users");
      });
  }, [callAuthorized, page, appliedEmail]);

  // Explicit submit, not live-as-you-type — same convention as
  // admin/movies' title search and admin/bookings' search form.
  function handleSearchSubmit(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedEmail(emailInput.trim());
  }

  const handleRoleToggle = (user: UserResponse) => {
    const newRole = user.role === "ADMIN" ? "CUSTOMER" : "ADMIN";
    setConfirmDialog({
      isOpen: true,
      title: "Change User Role",
      description: `Are you sure you want to change ${user.email}'s role to ${newRole}?`,
      action: async () => {
        try {
          await callAuthorized((token) => updateUserRole(token, user.id, { role: newRole }));
          setResult((prev) => {
            if (!prev) return prev;
            return {
              ...prev,
              data: {
                ...prev.data,
                content: prev.data.content.map((u) => (u.id === user.id ? { ...u, role: newRole } : u)),
              },
            };
          });
          closeDialog();
        } catch {
          setActionError("Failed to update role. Please try again.");
        }
      },
    });
  };

  const handleDelete = (user: UserResponse) => {
    setConfirmDialog({
      isOpen: true,
      title: "Delete User",
      description: `Are you sure you want to delete ${user.email}? This action cannot be undone.`,
      action: async () => {
        try {
          await callAuthorized((token) => deleteUser(token, user.id));
          setResult((prev) => {
            if (!prev) return prev;
            return {
              ...prev,
              data: { ...prev.data, content: prev.data.content.filter((u) => u.id !== user.id) },
            };
          });
          closeDialog();
        } catch (err: unknown) {
          if (err instanceof ApiError && err.status === 409) {
            setActionError("Can't delete: this user has booking records");
          } else {
            setActionError("Failed to delete. Please try again.");
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
        <h1 className="font-heading text-2xl font-semibold text-foreground">Admin · Users</h1>
        <p className="mt-1 text-sm text-muted-foreground">Manage platform users and permissions</p>
      </header>

      <form onSubmit={handleSearchSubmit} className="mb-6 flex max-w-sm items-end gap-2">
        <Field className="flex-1">
          <FieldLabel htmlFor="userEmailSearch">Search by email</FieldLabel>
          <Input
            id="userEmailSearch"
            placeholder="fan@example.com"
            value={emailInput}
            onChange={(event) => setEmailInput(event.target.value)}
          />
        </Field>
        <Button type="submit" variant="outline" className="h-11">
          <Search className="size-4" aria-hidden />
          Search
        </Button>
      </form>

      {error && (
        <div role="alert" className="mb-6 rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>Users</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-8 text-center text-sm text-muted-foreground">Loading...</div>
          ) : result.data.content.length === 0 ? (
            <div className="py-8 text-center text-sm text-muted-foreground">
              {appliedEmail ? `No users match "${appliedEmail}"` : "No users yet"}
            </div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <caption className="sr-only">Users</caption>
                <thead>
                  <tr className="border-b border-border bg-muted/50 text-left text-muted-foreground">
                    <th scope="col" className="px-3 py-2 font-medium">Email</th>
                    <th scope="col" className="px-3 py-2 font-medium">Name</th>
                    <th scope="col" className="px-3 py-2 font-medium">Role</th>
                    <th scope="col" className="px-3 py-2 font-medium">Joined</th>
                    <th scope="col" className="px-3 py-2 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {result.data.content.map((user) => (
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
                            Switch Role
                          </Button>
                          <Button
                            type="button"
                            variant="destructive"
                            size="sm"
                            disabled={user.id === currentUser?.id}
                            onClick={() => handleDelete(user)}
                          >
                            Delete
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
          {result && result.data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between">
              <p className="text-sm text-muted-foreground">
                {result.data.totalElements} total, page {result.data.number + 1} of {result.data.totalPages}
              </p>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={result.data.first || loading}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={result.data.last || loading}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
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
                  Cancel
                </Button>
                <Button type="button" variant="default" onClick={confirmDialog.action}>
                  Confirm
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </section>
  );
}
