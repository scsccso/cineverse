/**
 * Shared easing curve for framer-motion transitions across the app —
 * previously copy-pasted as a local `EASE_APPLE` const in four different
 * files (hero-carousel.tsx, page-transition.tsx, profile/page.tsx,
 * bookings/[id]/confirmed/page.tsx). One definition so a future tweak to
 * the curve doesn't need to be hunted down four times.
 */
export const EASE_APPLE = [0.22, 1, 0.36, 1] as const;
