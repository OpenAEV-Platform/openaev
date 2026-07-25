import { type Theme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';

import { adHocSeries } from '../../../actions/dashboards/dashboard-action';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { type EsSeries, type Filter, type FilterGroup, type Widget } from '../../../utils/api-types';

export interface PostureScoreEntry {
  success: number;
  failed: number;
}

/**
 * Severity band color for a posture score (kept in sync with the severity
 * bands of the PostureScore hero dialog).
 */
export const postureBandColor = (theme: Theme, score: number | null): string => {
  if (score === null) return theme.palette.text.secondary;
  if (score >= 75) return theme.palette.success.main;
  if (score >= 50) return theme.palette.warning.main;
  if (score >= 25) return '#ff7043';
  return theme.palette.error.main;
};

const filter = (key: string, values: string[]): Filter => ({
  id: generateFilterId(),
  key,
  mode: 'or',
  values,
  operator: 'eq',
});

const group = (...filters: Filter[]): FilterGroup => ({
  mode: 'and',
  filters,
});

// Per-bucket totals of a named series, keyed by the raw bucket key (the scoped
// entity id for side fields).
const bucketTotals = (series: EsSeries[] | null, name: string): Record<string, number> => {
  const data = (series ?? []).find(s => s.label === name)?.data ?? [];
  return data.reduce<Record<string, number>>((acc, bucket) => {
    const key = bucket.key ?? bucket.label;
    if (key) acc[key] = (acc[key] ?? 0) + (bucket.value ?? 0);
    return acc;
  }, {});
};

/**
 * Batched flavour of useExpectationPosture for list pages: one dashboard-engine
 * query returns the validated (SUCCESS / FAILED) expectation counts of every
 * entity of the current page, bucketed by the scoping side field. Feeds the
 * per-row posture score column.
 *
 * @param scopeField ES side field carrying the scope (e.g. `base_asset_side`,
 *                   `base_asset_group_side`).
 * @param entityIds  The ids of the currently displayed page.
 */
const usePostureScores = (
  scopeField: string,
  entityIds: string[],
): {
  loading: boolean;
  scores: Record<string, PostureScoreEntry>;
} => {
  const [series, setSeries] = useState<EsSeries[] | null>(null);

  // Stable dependency: the ids array is rebuilt on every render by callers.
  const idsKey = entityIds.join(',');

  useEffect(() => {
    const ids = idsKey ? idsKey.split(',') : [];
    if (ids.length === 0) {
      setSeries([]);
      return;
    }
    setSeries(null);
    const expectation = (status: string) => group(
      filter('base_entity', ['expectation-inject']),
      filter(scopeField, ids),
      filter('inject_expectation_status', [status]),
    );
    const config = {
      title: '',
      field: scopeField,
      series: ['SUCCESS', 'FAILED'].map(status => ({
        name: status,
        filter: expectation(status),
      })),
      mode: 'structural',
      stacked: false,
      limit: Math.max(ids.length, 10),
      widget_configuration_type: 'structural-histogram',
      time_range: 'ALL_TIME',
      date_attribute: 'base_created_at',
    } as unknown as Widget['widget_config'];
    adHocSeries(config)
      .then((result: { data: EsSeries[] }) => setSeries(result.data))
      .catch(() => setSeries([]));
  }, [scopeField, idsKey]);

  const scores = useMemo(() => {
    const successMap = bucketTotals(series, 'SUCCESS');
    const failedMap = bucketTotals(series, 'FAILED');
    const ids = idsKey ? idsKey.split(',') : [];
    return ids.reduce<Record<string, PostureScoreEntry>>((acc, id) => {
      acc[id] = {
        success: successMap[id] ?? 0,
        failed: failedMap[id] ?? 0,
      };
      return acc;
    }, {});
  }, [series, idsKey]);

  return {
    loading: series === null,
    scores,
  };
};

export default usePostureScores;
