import { BlockOutlined, GppMaybeOutlined, HelpOutlineOutlined, KeyboardArrowRight, ShieldOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ApexOptions } from 'apexcharts';
import { type CSSProperties, type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { fetchSecurityPlatform } from '../../../../actions/assets/securityPlatform-actions';
import { adHocEntities, adHocSeries } from '../../../../actions/dashboards/dashboard-action';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import Chart from '../../../../components/Chart';
import { DetailHero, DetailSections, Field, HeroStat, InformationGrid, SectionBlock, SectionLabel } from '../../../../components/common/detail/EntityDetailCommon';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../components/ItemSecurityPlatformType';
import ItemStatus from '../../../../components/ItemStatus';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { SECURITY_PLATFORM_BASE_URL } from '../../../../constants/BaseUrls';
import {
  type EsBase,
  type EsEntities,
  type EsInjectExpectation,
  type EsSeries,
  type Filter,
  type FilterGroup,
  type SearchPaginationInput,
  type SecurityPlatform,
  type SortField,
  type Widget,
} from '../../../../utils/api-types';
import { computeInjectExpectationLabel } from '../../../../utils/statusUtils';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import expectationIconByType, { expectationTypeIcon } from '../../common/ExpectationIconByType';
import ExpectationTypeChip from '../../workspaces/custom_dashboards/widgets/viz/list/elements/ExpectationTypeChip';
import navigationHandlers from '../../workspaces/custom_dashboards/widgets/viz/list/elements/ListNavigationHandler';
import SecurityPlatformPopover from './SecurityPlatformPopover';

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
  const navigate = useNavigate();
  const bodyItemsStyles = useBodyItemsStyles();
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

  // Latest missed expectations: standard app list (sort headers + plain line
  // items) driven by the standard search / filters / pagination toolbar. The
  // runtime search, filters, sort and page are translated into the ad-hoc list
  // query scoped to this platform's FAILED expectations.
  const MISSED_COLUMNS = ['inject_title', 'inject_expectation_type', 'inject_expectation_status', 'inject_expectation_score', 'base_created_at'];
  const [missedLoading, setMissedLoading] = useState(true);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    `security-platform-${securityPlatformId}-missed`,
    buildSearchPagination({ sorts: initSorting('base_created_at', 'DESC') }),
  );

  const missedInlineStyles: Record<string, CSSProperties> = {
    inject_title: { width: '34%' },
    inject_expectation_type: { width: '16%' },
    inject_expectation_status: { width: '20%' },
    inject_expectation_score: { width: '12%' },
    base_created_at: { width: '18%' },
  };

  const missedHeaders: Header[] = useMemo(() => [
    {
      field: 'inject_title',
      // The expectation's own "name" is just its type (e.g. "Detection"), so it
      // is redundant next to the Type column. Surface the inject (the attack that
      // was tested) instead - the piece of metadata that actually identifies the row.
      label: 'Inject',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => {
        const title = expectation.inject_title || expectation.base_representative || t('Unknown');
        return (
          <Tooltip title={expectation.inject_expectation_description || title} placement="bottom-start">
            <span>{title}</span>
          </Tooltip>
        );
      },
    },
    {
      field: 'inject_expectation_type',
      label: 'Type',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => <ExpectationTypeChip type={expectation.inject_expectation_type} />,
    },
    {
      field: 'inject_expectation_status',
      label: 'Result',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => {
        const label = computeInjectExpectationLabel(
          expectation.inject_expectation_status,
          expectation.inject_expectation_type,
        ) ?? '';
        return (
          <ItemStatus
            label={label}
            variant="inList"
            status={label}
            icon={expectationIconByType(expectation.inject_expectation_type, { fontSize: 14 })}
          />
        );
      },
    },
    {
      field: 'inject_expectation_score',
      // Obtained vs expected score - shows how far the platform fell short.
      label: 'Score',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => {
        const score = expectation.inject_expectation_score;
        const expected = expectation.inject_expectation_expected_score;
        if (score == null && expected == null) {
          return <>-</>;
        }
        return (
          <span>
            {Math.round(score ?? 0)}
            {expected != null ? ` / ${Math.round(expected)}` : ''}
          </span>
        );
      },
    },
    {
      field: 'base_created_at',
      label: 'Date',
      isSortable: true,
      value: (expectation: EsInjectExpectation) => <>{nsdt(expectation.base_created_at)}</>,
    },
  ], [t, nsdt]);

  const fetchMissed = (input: SearchPaginationInput): Promise<{ data: Page<EsBase> }> => {
    setMissedLoading(true);
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
    })).finally(() => setMissedLoading(false));
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
        action={(
          <SecurityPlatformPopover
            securityPlatform={{
              ...platform,
              type: 'security-platform',
            }}
            onUpdate={result => setPlatform(result)}
            onDelete={() => navigate(SECURITY_PLATFORM_BASE_URL)}
          />
        )}
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

      {/* Identity + performance trend side by side: the fields grid is short
          and the chart reads fine at half width, so one row keeps the
          overview compact. */}
      <DetailSections>
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
      </DetailSections>

      <div>
        <SectionLabel>{t('Latest missed expectations')}</SectionLabel>
        <PaginationComponentV2
          fetch={fetchMissed}
          searchPaginationInput={searchPaginationInput}
          setContent={setMissed}
          entityPrefix="inject_expectation"
          availableFilterNames={['inject_expectation_type', 'inject_expectation_status']}
          queryableHelpers={queryableHelpers}
        />
        <List>
          <ListItem
            divider={false}
            style={{
              paddingTop: 0,
              textTransform: 'uppercase',
            }}
            secondaryAction={<>&nbsp;</>}
          >
            <ListItemIcon />
            <ListItemText
              primary={(
                <SortHeadersComponentV2
                  headers={missedHeaders}
                  inlineStylesHeaders={missedInlineStyles}
                  sortHelpers={queryableHelpers.sortHelpers}
                />
              )}
            />
          </ListItem>
          {missedLoading
            ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={missedHeaders} headerStyles={missedInlineStyles} />
            : missed.map((element) => {
                const expectation = element as EsInjectExpectation;
                const LeadingIcon = expectationTypeIcon(expectation.inject_expectation_type);
                return (
                  <ListItem
                    key={expectation.base_id}
                    divider
                    disablePadding
                    secondaryAction={<KeyboardArrowRight color="action" />}
                  >
                    <ListItemButton
                      style={{ height: 50 }}
                      onClick={() => navigationHandlers['expectation-inject']?.(element, navigate)}
                    >
                      <ListItemIcon>
                        <LeadingIcon color="primary" />
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div style={bodyItemsStyles.bodyItems}>
                            {missedHeaders.map(header => (
                              <div
                                key={header.field}
                                style={{
                                  ...bodyItemsStyles.bodyItem,
                                  ...missedInlineStyles[header.field],
                                }}
                              >
                                {header.value?.(expectation)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })}
          {!missedLoading && missed.length === 0 && <Empty message={t('No data to display')} />}
        </List>
      </div>
    </Box>
  );
};

export default SecurityPlatformDetail;
