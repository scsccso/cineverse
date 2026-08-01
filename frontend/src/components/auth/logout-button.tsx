"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";
import { Button } from "@/components/ui/button";

export function LogoutButton({ className }: { className?: string }) {
  const { logout } = useAuth();
  const router = useRouter();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function handleLogout() {
    setIsLoggingOut(true);
    try {
      await logout();
    } finally {
      router.push("/login");
    }
  }

  return (
    <Button
      variant="outline"
      size="sm"
      className={className}
      disabled={isLoggingOut}
      onClick={handleLogout}
    >
      {isLoggingOut ? "退出中…" : "退出登录"}
    </Button>
  );
}
