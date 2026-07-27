import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { adHocSeries } from '../../../../actions/dashboards/dashboard-action';
import Chart from '../../../../components/Chart';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type EsSeries, type Filter, type FilterGroup, type Widget } from '../../../../utils/api-types';
import { sampleCountOverTimeSeries } from '../../../../utils/SampleCharts';
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
  /** ES side set field carrying the scope on injects (e.g. `base_assets_side`). */
  scopeField: string;
  /** The scoped entity id. */
  entityId: string;
  height?: number;
}

/**
 * Weekly count of injects that targeted one scoped entity (asset or asset
 * group), plotted over time. Sits next to the posture-score trend in the
 * Statistics tab of asset-side detail pages. Shows greyed-out sample data
 * (platform-wide convention) until real injects exist.
 */
const InjectsPlayedOverTimeChart: FunctionComponent<Props> = ({ scopeField, entityId, height = 280 }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  const [trend, setTrend] = useState<EsSeries[] | null>(null);

  useEffect(() => {
    setTrend(null);
    const trendConfig = {
      title: '',
      series: [
        {
          name: 'INJECTS',
          filter: group(
            filter('base_entity', ['inject']),
            filter(scopeField, [entityId], 'contains'),
          ),
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
  }, [scopeField, entityId]);

  const trendSeries = useMemo(() => {
    if (!trend) return [];
    const serie = trend.find(s => s.label === 'INJECTS');
    const buckets = (serie?.data ?? [])
      .filter(bucket => !!bucket.key)
      .map(bucket => ({
        x: bucket.key as string,
        y: bucket.value ?? 0,
      }))
      .sort((a, b) => a.x.localeCompare(b.x));
    return [{
      name: t('Injects played'),
      data: buckets,
    }];
  }, [trend, t]);

  const hasTrend = trendSeries.length > 0 && trendSeries[0].data.some(point => point.y > 0);

  const chartOptions = useMemo(() => buildTrendAreaOptions({
    theme,
    formatDate: (value: string) => nsdt(value),
    noDataText: t('No data to display'),
    percent: false,
    color: theme.palette.primary.main,
    singlePoint: (trendSeries[0]?.data.length ?? 0) <= 1,
  }), [theme, trendSeries, nsdt, t]);

  if (trend === null) return <Loader variant="inElement" />;
  return (
    <SamplePreview active={!hasTrend}>
      <Chart
        options={chartOptions}
        series={hasTrend ? trendSeries : sampleCountOverTimeSeries(t('Injects played'), theme)}
        type="area"
        width="100%"
        height={height}
      />
    </SamplePreview>
  );
};

export default InjectsPlayedOverTimeChart;
