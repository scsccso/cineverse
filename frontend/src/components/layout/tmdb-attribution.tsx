/**
 * Required by TMDB's API Terms of Use, §3 Attribution (verified against the
 * raw page text, not an AI-summarized paraphrase — see CLAUDE.md for the
 * full quote and why that distinction mattered here): "You must use the
 * TMDB logo to identify Your use of TMDB... Any use of any TMDB logos in
 * Your Application must be less prominent than the logos or marks that
 * primarily describe or identify Your Application... you must place the
 * following notice prominently in or on Your Application: 'This [website,
 * program, service, application, product] uses TMDB and the TMDB APIs but
 * is not endorsed, certified, or otherwise approved by TMDB.'"
 *
 * The logo is the required text, not a paraphrase — "website" is the
 * chosen substitution for the bracketed noun. `tmdb-attribution.svg` is
 * TMDB's own official "short" attribution mark (downloaded verbatim from
 * https://www.themoviedb.org/about/logos-attribution, unmodified), sized
 * well below the site's own wordmark (Navbar/AdminHeader logos render at
 * `size-5` = 20px; this renders at 14px) to satisfy the "less prominent"
 * requirement rather than just asserting it in a comment.
 *
 * Rendered as its own <footer> so each layout only needs to place this
 * component as a sibling, not build footer chrome around it — see
 * CustomerLayout/AdminLayout for the two mount points (one shared
 * component, not two independent implementations).
 */
export function TmdbAttribution() {
  return (
    <footer className="border-t border-border px-6 py-3">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-2 gap-y-1 text-center text-xs text-muted-foreground/80">
        {/* eslint-disable-next-line @next/next/no-img-element -- a static
            local SVG with a fixed intrinsic size doesn't need next/image's
            optimization pipeline; plain <img> also avoids the optimizer
            treating an already-vector asset as something to re-encode. */}
        <img src="/tmdb-attribution.svg" alt="TMDB" className="h-3.5 w-auto shrink-0 opacity-70" />
        <span>
          This website uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.
        </span>
      </div>
    </footer>
  );
}
