import Image from "next/image";
import { resolveMediaUrl } from "@/lib/api/client";
import { cn } from "@/lib/utils";

interface MovieBackdropProps {
  backdropUrl: string;
  /** Bottom-to-top gradient overlay, e.g. "bg-gradient-to-t from-background via-background/40 to-background/10" — left as a prop rather than hardcoded because the two call sites (Hero, movie detail) use different opacity stops and there was no request to unify those, only the crop/sharpness bugs below. */
  gradientClassName: string;
  priority?: boolean;
}

/**
 * The Image + gradient-overlay pair shared by HeroCarousel and the movie
 * detail page's backdrop banner — extracted after a real bug where a fix
 * (object-position, see below) landed on one call site and silently never
 * reached the other, because each had its own copy of this markup. Only
 * the image/gradient layer is shared, not the surrounding sizing wrapper
 * (`h-[70vh]` vs `h-[50vh]`, absolute-fill vs static) or the card layout
 * around it — those two contexts differ enough (Hero crossfades between
 * movies inside a motion.div; the detail page is a single static banner)
 * that folding them into one component would need more conditional
 * branching than the ~10 lines it'd save. Callers own their own sizing/
 * positioning wrapper and drop this inside it.
 *
 * `object-position: 50% 30%` (not object-cover's default 50% 50%) — both
 * call sites' containers are proportionally wider than the 16:9 TMDB
 * backdrops, so centered object-cover crops the top and bottom evenly;
 * most backdrop compositions put the interesting content (sky, wide
 * shots) in the upper two-thirds, so biasing the visible window upward
 * loses less than a centered crop does. See CLAUDE.md for the before/
 * after comparison this value was picked against.
 *
 * `priority` (not `preload`, which isn't a real next/image prop and was
 * silently ignored at both call sites before this extraction — an actual
 * latent bug, not a stylistic choice) hints the browser to load this
 * image eagerly and with high fetch priority, appropriate for both this
 * page's above-the-fold banner and the Hero's initially-visible slide.
 */
export function MovieBackdrop({ backdropUrl, gradientClassName, priority }: MovieBackdropProps) {
  return (
    <>
      <Image
        src={resolveMediaUrl(backdropUrl)}
        alt=""
        fill
        priority={priority}
        sizes="100vw"
        className="object-cover"
        style={{ objectPosition: "50% 30%" }}
      />
      <div className={cn("absolute inset-0", gradientClassName)} />
    </>
  );
}
