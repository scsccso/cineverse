"use client";

import { useId } from "react";
import { Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { resolvePreset, type DateRange, type DateRangePreset } from "@/lib/admin/date-range";

const PRESET_OPTIONS: { value: Exclude<DateRangePreset, "custom">; label: string }[] = [
  { value: "today", label: "今日" },
  { value: "7d", label: "近 7 天" },
  { value: "30d", label: "近 30 天" },
];

export interface DateRangeFilterValue extends DateRange {
  preset: DateRangePreset;
}

interface DateRangeFilterProps {
  value: DateRangeFilterValue;
  onChange: (next: DateRangeFilterValue) => void;
}

/**
 * One filter row scoping every chart/stat/table below it (see dataviz
 * skill's interaction.md) — presets first since that's what most admins
 * reach for, custom range behind an explicit toggle. Every button carries
 * aria-pressed and a check icon on the active one, not just a fill-color
 * change, so selection state doesn't depend on color perception alone.
 */
export function DateRangeFilter({ value, onChange }: DateRangeFilterProps) {
  const fromId = useId();
  const toId = useId();

  function selectPreset(preset: Exclude<DateRangePreset, "custom">) {
    onChange({ preset, ...resolvePreset(preset) });
  }

  function switchToCustom() {
    onChange({ preset: "custom", from: value.from, to: value.to });
  }

  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="flex flex-wrap gap-2" role="group" aria-label="预设时间范围">
        {PRESET_OPTIONS.map((option) => (
          <PresetButton
            key={option.value}
            label={option.label}
            selected={value.preset === option.value}
            onClick={() => selectPreset(option.value)}
          />
        ))}
        <PresetButton label="自定义" selected={value.preset === "custom"} onClick={switchToCustom} />
      </div>

      {value.preset === "custom" && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor={fromId}>起始日期</Label>
            <Input
              id={fromId}
              type="date"
              className="h-11"
              value={value.from}
              max={value.to}
              onChange={(event) => onChange({ preset: "custom", from: event.target.value, to: value.to })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor={toId}>结束日期</Label>
            <Input
              id={toId}
              type="date"
              className="h-11"
              value={value.to}
              min={value.from}
              onChange={(event) => onChange({ preset: "custom", from: value.from, to: event.target.value })}
            />
          </div>
        </div>
      )}
    </div>
  );
}

function PresetButton({ label, selected, onClick }: { label: string; selected: boolean; onClick: () => void }) {
  return (
    <Button type="button" variant={selected ? "default" : "outline"} aria-pressed={selected} onClick={onClick}>
      {selected && <Check className="size-3.5" aria-hidden data-icon="inline-start" />}
      {label}
    </Button>
  );
}
