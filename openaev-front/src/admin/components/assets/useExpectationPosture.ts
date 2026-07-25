import { useEffect, useState } from 'react';

import { adHocSeries } from '../../../actions/dashboards/dashboard-action';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { type EsSeries, type Filter, type FilterGroup, type Widget } from '../../../utils/api-types';

// Every expectation type the platform can validate (kept in sync with the
// backend EXPECTATION_TYPE enum). Explicit per-type series are queried so the
// totals never depend on how the engine keys a structural breakdown.
const EXPECTATION_TYPES = ['PREVENTION', 'DETECTION', 'VULNERABILITY', 'MANUAL', 'ARTICLE', 'CHALLENGE'];

export interface PostureBreakdownEntry {
  key: string;
  success: number;
  failed: number;
}

export interface ExpectationPosture {
  /** Null while the series query is in flight. */
  loading: boolean;
  /** Expectations met (SUCCESS). */
  success: number;
  /** Expectations missed (FAILED). */
  failed: number;
  /** Total validated expectations (success + failed - pending ones excluded). */
  tested: number;
  /** Per-expectation-type contribution (only types that actually ran). */
  breakdown: PostureBreakdownEntry[];
}

const filter = (key: string, values: string[], operator: Filter['operator'] = 'eq'): Filter => ({
  id: generateFilterId(),
  key,
  mode: 'or',
  values,
  operator,
});

const group = (...filters: Filter[]): FilterGroup => ({
  mode: 'and',
  filters,
});

// Total of a named series, summed across all buckets so the result never
// depends on the breakdown key format (same approach as SecurityPlatformDetail).
const namedTotal = (series: EsSeries[] | null, name: string) =>
  ((series ?? []).find(s => s.label === name)?.data ?? []).reduce((acc, bucket) => acc + (bucket.value ?? 0), 0);

/**
 * Aggregates the validated (SUCCESS / FAILED) expectations scoped to one
 * entity via the dashboard engine, broken down by expectation type. Feeds the
 * posture score and the "expectations tested" hero stats on asset-side detail
 * pages.
 *
 * @param scopeField    ES side field carrying the scope (e.g. `base_asset_side`).
 * @param entityId      The scoped entity id.
 * @param scopeOperator `eq` for single-value side fields (default), `contains`
 *                      for set fields (e.g. `base_security_platforms_side`).
 */
const useExpectationPosture = (
  scopeField: string,
  entityId: string,
  scopeOperator: Filter['operator'] = 'eq',
): ExpectationPosture => {
  const [series, setSeries] = useState<EsSeries[] | null>(null);

  useEffect(() => {
    setSeries(null);
    const expectation = (extra: Filter[]) => group(
      filter('base_entity', ['expectation-inject']),
      filter(scopeField, [entityId], scopeOperator),
      ...extra,
    );
    const config = {
      title: '',
      field: 'inject_expectation_type',
      series: EXPECTATION_TYPES.flatMap(type => ['SUCCESS', 'FAILED'].map(status => ({
        name: `${type}_${status}`,
        filter: expectation([
          filter('inject_expectation_type', [type]),
          filter('inject_expectation_status', [status]),
        ]),
      }))),
      mode: 'structural',
      stacked: false,
      limit: 100,
      widget_configuration_type: 'structural-histogram',
      time_range: 'ALL_TIME',
      date_attribute: 'base_created_at',
    } as unknown as Widget['widget_config'];
    // Cancellation guard: if the scoped entity changes while the query is in
    // flight, the stale response must not overwrite the newer one.
    let cancelled = false;
    adHocSeries(config)
      .then((result: { data: EsSeries[] }) => {
        if (!cancelled) setSeries(result.data);
      })
      .catch(() => {
        if (!cancelled) setSeries([]);
      });
    return () => {
      cancelled = true;
    };
  }, [scopeField, entityId, scopeOperator]);

  const breakdown = EXPECTATION_TYPES
    .map(type => ({
      key: type,
      success: namedTotal(series, `${type}_SUCCESS`),
      failed: namedTotal(series, `${type}_FAILED`),
    }))
    .filter(entry => entry.success + entry.failed > 0);
  const success = breakdown.reduce((acc, entry) => acc + entry.success, 0);
  const failed = breakdown.reduce((acc, entry) => acc + entry.failed, 0);

  return {
    loading: series === null,
    success,
    failed,
    tested: success + failed,
    breakdown,
  };
};

export default useExpectationPosture;
