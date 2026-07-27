import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { adHocSeries } from '../../../../actions/dashboards/dashboard-action';
import Chart from '../../../../components/Chart';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type EsSeries, type Filter, type FilterGroup, type Widget } from '../../../../utils/api-types';
import { sampleSuccessRateSeries } from '../../../../utils/SampleCharts';
import SamplePreview from '../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import buildTrendAreaOptions from './trendChartOptions';

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

interface Props {
  /** ES side field carrying the scope (e.g. `base_asset_side`). */
  scopeField: string;
  /** The scoped entity id. */
  entityId: string;
  /** `eq` for single-value side fields (default), `contains` for set fields. */
  scopeOperator?: Filter['operator'];
  height?: number;
}

/**
 * Weekly posture-score trend for one scoped entity: the share of validated
 * expectations (SUCCESS vs FAILED) the defenses met, per week - the exact
 * formula behind the hero PostureScore gauge, plotted over time. Shared by
 * the security platform, asset and asset group detail pages. Shows greyed-out
 * sample data (platform-wide convention) until real validations exist.
 */
const PostureScoreOverTimeChart: FunctionComponent<Props> = ({ scopeField, entityId, scopeOperator = 'eq', height = 280 }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  const [trend, setTrend] = useState<EsSeries[] | null>(null);

  useEffect(() => {
    setTrend(null);
    const expectation = (extra: Filter[]) => group(
      filter('base_entity', ['expectation-inject']),
      filter(scopeField, [entityId], scopeOperator),
      ...extra,
    );
    const trendConfig = {
      title: '',
      series: [
        {
          name: 'SUCCESS',
          filter: expectation([filter('inject_expectation_status', ['SUCCESS'])]),
        },
        {
          name: 'FAILED',
          filter: expectation([filter('inject_expectation_status', ['FAILED'])]),
        },
      ],
      mode: 'temporal',
      stacked: false,
      interval: 'week',
      widget_configuration_type: 'temporal-histogram',
      time_range: 'ALL_TIME',
      date_attribute: 'base_created_at',
    } as unknown as Widget['widget_config'];
    // Cancellation guard: a stale response for a previous scope must not
    // overwrite the current one.
    let cancelled = false;
    adHocSeries(trendConfig)
      .then((r: { data: EsSeries[] }) => {
        if (!cancelled) setTrend(r.data);
      })
      .catch(() => {
        if (!cancelled) setTrend([]);
      });
    return () => {
      cancelled = true;
    };
  }, [scopeField, entityId, scopeOperator]);

  const trendSeries = useMemo(() => {
    if (!trend) return [];
    const success = trend.find(s => s.label === 'SUCCESS');
    const failed = trend.find(s => s.label === 'FAILED');
    const at = (serie: EsSeries | undefined, key: string) => (serie?.data ?? []).find(d => d.key === key)?.value ?? 0;
    const keys = [...new Set([
      ...(success?.data ?? []).map(d => d.key ?? ''),
      ...(failed?.data ?? []).map(d => d.key ?? ''),
    ])].filter(Boolean).sort();
    return [{
      name: t('Posture score'),
      data: keys.map((key) => {
        const s = at(success, key);
        const f = at(failed, key);
        const total = s + f;
        return {
          x: key,
          y: total === 0 ? 0 : Math.round((s / total) * 100),
        };
      }),
    }];
  }, [trend, t]);

  const hasTrend = trendSeries.length > 0 && trendSeries[0].data.length > 0;

  const chartOptions = useMemo(() => buildTrendAreaOptions({
    theme,
    formatDate: (value: string) => nsdt(value),
    noDataText: t('No data to display'),
    percent: true,
    color: theme.palette.success.main,
    singlePoint: (trendSeries[0]?.data.length ?? 0) <= 1,
  }), [theme, trendSeries, nsdt, t]);

  if (trend === null) return <Loader variant="inElement" />;
  return (
    <SamplePreview active={!hasTrend}>
      <Chart
        options={chartOptions}
        series={hasTrend ? trendSeries : sampleSuccessRateSeries(t('Posture score'), theme)}
        type="area"
        width="100%"
        height={height}
      />
    </SamplePreview>
  );
};

export default PostureScoreOverTimeChart;
