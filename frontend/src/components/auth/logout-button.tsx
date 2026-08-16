"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface LogoutButtonProps {
  className?: string;
  /** Icon-only rendering for the collapsed admin sidebar footer, where
   * there's no room for the text label — every other existing caller
   * (customer Navbar) omits this and renders exactly as before. h-11 w-11
   * rather than any of Button's icon-* sizes (all under 44px): this sits in
   * primary sidebar navigation now, not the "secondary utility control"
   * context button.tsx's size comment describes the default text variant
   * for. */
  iconOnly?: boolean;
}

export function LogoutButton({ className, iconOnly = false }: LogoutButtonProps) {
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

  if (iconOnly) {
    return (
      <Button
        variant="outline"
        className={cn("h-11 w-11", className)}
        disabled={isLoggingOut}
        onClick={handleLogout}
        aria-label={isLoggingOut ? "Logging out…" : "Log out"}
        title="Log out"
      >
        <LogOut className="size-4" aria-hidden />
      </Button>
    );
  }

  return (
    <Button
      variant="outline"
      size="sm"
      className={className}
      disabled={isLoggingOut}
      onClick={handleLogout}
    >
      {isLoggingOut ? "Logging out…" : "Log out"}
    </Button>
  );
}
