"use client";

import { useState } from "react";
import type { MovieStatus } from "@/lib/api/types";
import { MOVIE_STATUS_BADGE_VARIANT, MOVIE_STATUS_LABELS, MOVIE_STATUS_OPTIONS } from "@/lib/movie-status";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

interface MovieStatusControlProps {
  currentStatus: MovieStatus;
  onChangeStatus: (next: MovieStatus) => void;
  isChanging: boolean;
}

/** Read-only status badge + a general-purpose "change status" picker.
 * Status can no longer be changed through MovieForm's PUT (see its doc
 * comment) — this is the only surface left that reaches all 6 possible
 * transitions, not just the two nudge-driven shortcuts (Switch to Now
 * Playing / Mark as Ended) that only cover the two most common cases.
 *
 * Uncontrolled-by-prop on purpose: `selected` only tracks the admin's
 * in-progress pick, it doesn't get synced back to currentStatus via an
 * effect. The parent instead remounts this component (key={movie.status})
 * whenever the status actually changes — from this control or from either
 * one-click nudge — so the picker resets to the new value without a
 * set-state-in-effect. */
export function MovieStatusControl({ currentStatus, onChangeStatus, isChanging }: MovieStatusControlProps) {
  const [selected, setSelected] = useState<MovieStatus>(currentStatus);
  const isUnchanged = selected === currentStatus;

  return (
    <div className="mb-6 flex flex-wrap items-end gap-3">
      <div>
        <span className="mb-1.5 block text-sm font-medium text-foreground">Status</span>
        <Badge variant={MOVIE_STATUS_BADGE_VARIANT[currentStatus]}>{MOVIE_STATUS_LABELS[currentStatus]}</Badge>
      </div>
      <div>
        <label htmlFor="movie-status-picker" className="mb-1.5 block text-sm font-medium text-foreground">
          Change to
        </label>
        <select
          id="movie-status-picker"
          className="h-11 rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          value={selected}
          onChange={(event) => setSelected(event.target.value as MovieStatus)}
        >
          {MOVIE_STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
      <Button
        type="button"
        variant="outline"
        className="h-11"
        disabled={isUnchanged || isChanging}
        onClick={() => onChangeStatus(selected)}
      >
        {isChanging ? "Updating…" : "Update Status"}
      </Button>
    </div>
  );
}
