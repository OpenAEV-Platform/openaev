import { BlockOutlined, GppMaybeOutlined, ShieldOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ApexOptions } from 'apexcharts';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { fetchSecurityPlatform } from '../../../../actions/assets/securityPlatform-actions';
import { adHocEntities, adHocSeries } from '../../../../actions/dashboards/dashboard-action';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import Chart from '../../../../components/Chart';
import { DetailHero, Field, HeroStat, InformationGrid, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../components/ItemSecurityPlatformType';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { SECURITY_PLATFORM_BASE_URL } from '../../../../constants/BaseUrls';
import {
  type EsBase,
  type EsEntities,
  type EsSeries,
  type Filter,
  type FilterGroup,
  type ListConfiguration,
  type SearchPaginationInput,
  type SecurityPlatform,
  type SortField,
  type Widget,
} from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import ListWidget from '../../workspaces/custom_dashboards/widgets/viz/list/ListWidget';

const PLATFORM_FILTER_KEY = 'base_security_platforms_side';

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

// Sum every bucket of a returned series into a single total.
const seriesTotal = (serie?: EsSeries) => (serie?.data ?? []).reduce((acc, bucket) => acc + (bucket.value ?? 0), 0);

// Total of a named series (matched on the label the backend echoes back), summed
// across all buckets so the result never depends on the breakdown key format.
const namedTotal = (series: EsSeries[] | null, name: string) =>
  seriesTotal((series ?? []).find(s => s.label === name));

const rate = (success: number, failed: number) => {
  const total = success + failed;
  return total === 0 ? null : Math.round((success / total) * 100);
};

// Full-page overview for a single security platform: identity, posture KPIs,
// a prevention/detection performance trend over time, and the latest expectations
// this platform missed - all fed by the dashboard engine scoped to this platform.
const SecurityPlatformDetail: FunctionComponent = () => {
  const { t, fldt, nsdt } = useFormatter();
  const theme = useTheme();
  const { securityPlatformId } = useParams() as { securityPlatformId: string };

  const [platform, setPlatform] = useState<SecurityPlatform | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetchSecurityPlatform(securityPlatformId)
      .then((result: { data: SecurityPlatform }) => setPlatform(result.data))
      .catch(() => setPlatform(null))
      .finally(() => setLoading(false));
  }, [securityPlatformId]);

  const platformFilter = useMemo(() => filter(PLATFORM_FILTER_KEY, [securityPlatformId], 'contains'), [securityPlatformId]);

  // -- KPIs: structural breakdown by expectation type (SUCCESS vs FAILED)
  const [byType, setByType] = useState<EsSeries[] | null>(null);
  // -- Trend: temporal SUCCESS vs FAILED
  const [trend, setTrend] = useState<EsSeries[] | null>(null);
  // -- Latest missed expectations (standard paginated list, dashboard look & feel)
  const [missed, setMissed] = useState<EsBase[]>([]);

  useEffect(() => {
    const expectation = (extra: Filter[]) => group(filter('base_entity', ['expectation-inject']), platformFilter, ...extra);

    const typeStatus = (type: string, status: string) => expectation([
      filter('inject_expectation_type', [type]),
      filter('inject_expectation_status', [status]),
    ]);

    // Explicit per-type/per-status series so KPI totals never depend on how the
    // engine keys a structural breakdown.
    const byTypeConfig = {
      title: '',
      field: 'inject_expectation_type',
      series: [
        {
          name: 'DETECTION_SUCCESS',
          filter: typeStatus('DETECTION', 'SUCCESS'),
        },
        {
          name: 'DETECTION_FAILED',
          filter: typeStatus('DETECTION', 'FAILED'),
        },
        {
          name: 'PREVENTION_SUCCESS',
          filter: typeStatus('PREVENTION', 'SUCCESS'),
        },
        {
          name: 'PREVENTION_FAILED',
          filter: typeStatus('PREVENTION', 'FAILED'),
        },
        {
          name: 'ALL_SUCCESS',
          filter: expectation([filter('inject_expectation_status', ['SUCCESS'])]),
        },
        {
          name: 'ALL_FAILED',
          filter: expectation([filter('inject_expectation_status', ['FAILED'])]),
        },
      ],
      mode: 'structural',
      stacked: false,
      limit: 100,
      widget_configuration_type: 'structural-histogram',
      time_range: 'ALL_TIME',
      date_attribute: 'base_created_at',
    } as unknown as Widget['widget_config'];

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

    adHocSeries(byTypeConfig).then((r: { data: EsSeries[] }) => setByType(r.data)).catch(() => setByType([]));
    adHocSeries(trendConfig).then((r: { data: EsSeries[] }) => setTrend(r.data)).catch(() => setTrend([]));
  }, [securityPlatformId, platformFilter]);

  // Latest missed expectations: rendered with the custom-dashboard list widget
  // (normal lines) and driven by the standard search / filters / pagination
  // toolbar. The runtime search, filters, sort and page are translated into the
  // ad-hoc list query scoped to this platform's FAILED expectations.
  const MISSED_COLUMNS = ['inject_expectation_name', 'inject_expectation_type', 'inject_expectation_status', 'base_created_at'];
  const missedListConfig = { columns: MISSED_COLUMNS } as unknown as ListConfiguration;
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    `security-platform-${securityPlatformId}-missed`,
    buildSearchPagination({ sorts: initSorting('base_created_at', 'DESC') }),
  );

  const fetchMissed = (input: SearchPaginationInput): Promise<{ data: Page<EsBase> }> => {
    const runtimeFilters = input.filterGroup?.filters ?? [];
    const searchFilters = input.textSearch
      ? [filter('base_representative', [input.textSearch], 'contains')]
      : [];
    const missedConfig = {
      title: '',
      series: [],
      perspective: {
        name: '',
        filter: group(
          filter('base_entity', ['expectation-inject']),
          platformFilter,
          filter('inject_expectation_status', ['FAILED']),
          ...runtimeFilters,
          ...searchFilters,
        ),
      },
      columns: MISSED_COLUMNS,
      sorts: (input.sorts ?? []).map((s: SortField) => ({
        fieldName: s.property,
        direction: s.direction,
      })),
      widget_configuration_type: 'list',
      time_range: 'ALL_TIME',
      date_attribute: 'base_created_at',
    } as unknown as Widget['widget_config'];
    return adHocEntities(missedConfig, undefined, {
      page: input.page,
      size: input.size,
    }).then((r: { data: EsEntities }) => ({
      data: {
        content: (r.data.es_datas ?? []) as EsBase[],
        totalElements: r.data.total ?? 0,
        totalPages: r.data.page_size ? Math.ceil((r.data.total ?? 0) / r.data.page_size) : 0,
        pageable: { pageNumber: r.data.page_number ?? 0 },
      } as Page<EsBase>,
    }));
  };

  const kpis = useMemo(() => {
    const detectionRate = rate(namedTotal(byType, 'DETECTION_SUCCESS'), namedTotal(byType, 'DETECTION_FAILED'));
    const preventionRate = rate(namedTotal(byType, 'PREVENTION_SUCCESS'), namedTotal(byType, 'PREVENTION_FAILED'));
    const tested = namedTotal(byType, 'ALL_SUCCESS') + namedTotal(byType, 'ALL_FAILED');
    const missedCount = namedTotal(byType, 'ALL_FAILED');
    return {
      detectionRate,
      preventionRate,
      tested,
      missedCount,
    };
  }, [byType]);

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
      name: t('Success rate'),
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

  const chartOptions: ApexOptions = useMemo(() => ({
    chart: {
      type: 'area',
      background: 'transparent',
      toolbar: { show: false },
      zoom: { enabled: false },
      foreColor: theme.palette.text.secondary,
      fontFamily: '"IBM Plex Sans", sans-serif',
      parentHeightOffset: 0,
    },
    theme: { mode: theme.palette.mode },
    colors: [theme.palette.success.main],
    dataLabels: { enabled: false },
    stroke: {
      curve: 'smooth',
      width: 2.5,
      lineCap: 'round',
    },
    fill: {
      type: 'gradient',
      gradient: {
        shadeIntensity: 1,
        opacityFrom: 0.3,
        opacityTo: 0,
        stops: [0, 95],
      },
    },
    markers: {
      size: trendSeries[0]?.data.length <= 1 ? 5 : 0,
      strokeWidth: 2,
      strokeColors: theme.palette.background.paper,
      hover: { size: 6 },
    },
    grid: {
      borderColor: alpha(theme.palette.text.primary, 0.08),
      strokeDashArray: 4,
      xaxis: { lines: { show: false } },
      yaxis: { lines: { show: true } },
    },
    xaxis: {
      type: 'category',
      tickPlacement: 'on',
      axisBorder: { show: false },
      axisTicks: { show: false },
      labels: {
        rotate: 0,
        hideOverlappingLabels: true,
        formatter: (value: string) => (value ? nsdt(value) : value),
        style: { fontSize: '11px' },
      },
      tooltip: { enabled: false },
    },
    yaxis: {
      min: 0,
      max: 100,
      tickAmount: 5,
      labels: {
        formatter: (value: number) => `${Math.round(value)}%`,
        style: { fontSize: '11px' },
      },
    },
    tooltip: {
      theme: theme.palette.mode,
      x: { formatter: (value: number | string) => (value ? nsdt(String(value)) : String(value)) },
      y: { formatter: (value: number) => `${Math.round(value)}%` },
    },
    noData: { text: t('No data to display') },
  }), [theme, trendSeries, nsdt, t]);

  if (loading) {
    return <Loader />;
  }
  if (!platform) {
    return <NotFound />;
  }

  const logo = (
    <img
      src={buildTenantApiPath(`/api/images/security_platforms/id/${platform.asset_id}/${theme.palette.mode}`)}
      alt={platform.asset_name}
      style={{
        width: 32,
        height: 32,
        borderRadius: 4,
      }}
    />
  );

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      paddingBottom: 5,
    }}
    >
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Security platforms'),
            link: SECURITY_PLATFORM_BASE_URL,
          },
          {
            label: platform.asset_name,
            current: true,
          },
        ]}
      />

      <DetailHero
        iconNode={logo}
        title={platform.asset_name}
        chips={<ItemSecurityPlatformType type={platform.security_platform_type} size="medium" />}
        stats={(
          <>
            <HeroStat
              icon={ShieldOutlined}
              label={t('Prevention rate')}
              value={kpis.preventionRate === null ? '-' : `${kpis.preventionRate}%`}
              color={theme.palette.success.main}
            />
            <HeroStat
              icon={GppMaybeOutlined}
              label={t('Detection rate')}
              value={kpis.detectionRate === null ? '-' : `${kpis.detectionRate}%`}
              color={theme.palette.primary.main}
            />
            <HeroStat
              icon={TrackChangesOutlined}
              label={t('Expectations tested')}
              value={kpis.tested}
            />
            <HeroStat
              icon={BlockOutlined}
              label={t('Missed expectations')}
              value={kpis.missedCount}
              color={theme.palette.warning.main}
            />
          </>
        )}
      />

      <InformationGrid title={t('Information')}>
        <Field label={t('Type')}>
          <ItemSecurityPlatformType type={platform.security_platform_type} />
        </Field>
        <Field label={t('Description')}>
          {platform.asset_description
            ? <ExpandableMarkdown source={platform.asset_description} limit={300} />
            : '-'}
        </Field>
        <Field label={t('Tags')}>
          <ItemTags variant="list" tags={platform.asset_tags} />
        </Field>
        <Field label={t('Creation date')}>{fldt(platform.asset_created_at)}</Field>
        <Field label={t('Update date')}>{fldt(platform.asset_updated_at)}</Field>
      </InformationGrid>

      <SectionBlock title={t('Performance over time')}>
        {(() => {
          if (trend === null) return <Loader variant="inElement" />;
          if (!hasTrend) return <Empty message={t('No results yet for this security platform.')} />;
          return (
            <Chart
              options={chartOptions}
              series={trendSeries}
              type="area"
              width="100%"
              height={280}
            />
          );
        })()}
      </SectionBlock>

      <SectionBlock title={t('Latest missed expectations')}>
        <PaginationComponentV2
          fetch={fetchMissed}
          searchPaginationInput={searchPaginationInput}
          setContent={setMissed}
          entityPrefix="inject_expectation"
          availableFilterNames={['inject_expectation_type', 'inject_expectation_status']}
          queryableHelpers={queryableHelpers}
        />
        <Box sx={{ height: 480 }}>
          <ListWidget
            widgetConfig={missedListConfig}
            elements={missed}
            currentPageNumber={searchPaginationInput.page}
            elementsPerPage={searchPaginationInput.size}
            totalElements={queryableHelpers.paginationHelpers.getTotalElements()}
            onPaginationChange={() => {}}
            hidePagination
          />
        </Box>
      </SectionBlock>
    </Box>
  );
};

export default SecurityPlatformDetail;
