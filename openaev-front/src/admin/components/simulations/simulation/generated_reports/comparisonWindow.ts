/**
 * Shared "comparison window" selector used by every report scope that
 * aggregates multiple runs (Scenario and Global reports): a fixed preset
 * list (Last run / Last 1 week / Last 1 month / Custom range) plus the
 * date-floor resolution logic, so the exact same control/behavior is
 * available consistently across report types instead of being duplicated
 * per-scope.
 */
export type ComparisonWindow = 'LAST_RUN' | 'LAST_WEEK' | 'LAST_MONTH' | 'CUSTOM';

export interface ComparisonWindowInput {
  window: ComparisonWindow;
  /** Only used when `window === 'CUSTOM'`. */
  startDate?: string;
  endDate?: string;
}

export const WINDOW_OPTIONS: {
  key: ComparisonWindow;
  label: string;
}[] = [
  {
    key: 'LAST_RUN',
    label: 'Last run',
  },
  {
    key: 'LAST_WEEK',
    label: 'Last 1 week',
  },
  {
    key: 'LAST_MONTH',
    label: 'Last 1 month',
  },
  {
    key: 'CUSTOM',
    label: 'Custom range',
  },
];

export const windowLabelFor = (w: ComparisonWindow): string => WINDOW_OPTIONS.find(o => o.key === w)?.label ?? w;

const MS_PER_DAY = 1000 * 60 * 60 * 24;

/**
 * Resolves the date floor for a comparison window. Returns `null` for
 * `LAST_RUN`, which is handled separately by callers (keeping only the most
 * recent run(s) rather than filtering by an absolute date).
 */
export const windowStartDate = (input: ComparisonWindowInput): Date | null => {
  if (input.window === 'CUSTOM' && input.startDate) return new Date(input.startDate);
  if (input.window === 'LAST_WEEK') return new Date(Date.now() - 7 * MS_PER_DAY);
  if (input.window === 'LAST_MONTH') return new Date(Date.now() - 30 * MS_PER_DAY);
  return null;
};
