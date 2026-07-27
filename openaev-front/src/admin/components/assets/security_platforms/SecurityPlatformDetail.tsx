import { BlockOutlined, GppMaybeOutlined, HelpOutlineOutlined, KeyboardArrowRight, ShieldOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { fetchSecurityPlatform } from '../../../../actions/assets/securityPlatform-actions';
import { adHocEntities } from '../../../../actions/dashboards/dashboard-action';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, HeroStat, InformationGrid, SectionBlock, SectionLabel } from '../../../../components/common/detail/EntityDetailCommon';
import { buildFilter, generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
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
  type Filter,
  type FilterGroup,
  type SearchPaginationInput,
  type SecurityPlatform,
  type SortField,
  type Widget,
} from '../../../../utils/api-types';
import { computeInjectExpectationLabel, computeStatusStyle } from '../../../../utils/statusUtils';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import expectationIconByType, { expectationTypeIcon } from '../../common/ExpectationIconByType';
import ExpectationTypeChip from '../../workspaces/custom_dashboards/widgets/viz/list/elements/ExpectationTypeChip';
import InjectExpectationSourceFragment from '../../workspaces/custom_dashboards/widgets/viz/list/elements/InjectExpectationSourceFragment';
import { getNavigationUrl } from '../../workspaces/custom_dashboards/widgets/viz/list/elements/ListNavigationHandler';
import PostureScore from '../PostureScore';
import PostureScoreOverTimeChart from '../statistics/PostureScoreOverTimeChart';
import useExpectationPosture from '../useExpectationPosture';
import SecurityPlatformPopover from './SecurityPlatformPopover';
import isCollectorManaged from './securityPlatformUtils';

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

const rate = (success: number, failed: number) => {
  const total = success + failed;
  return total === 0 ? null : Math.round((success / total) * 100);
};

// Full-page overview for a single security platform: identity, posture KPIs,
// the posture score trend over time, and the latest expectations this
// platform missed - all fed by the dashboard engine scoped to this platform.
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

  // -- KPIs + posture: per-pillar SUCCESS vs FAILED, shared with the asset hero
  const posture = useExpectationPosture(PLATFORM_FILTER_KEY, securityPlatformId, 'contains');
  // -- Latest expectations (standard paginated list, dashboard look & feel)
  const [missed, setMissed] = useState<EsBase[]>([]);

  // Latest expectations: standard app list (sort headers + plain line items)
  // driven by the standard search / filters / pagination toolbar. The runtime
  // search, filters, sort and page are translated into the ad-hoc list query
  // scoped to this platform's expectations. The list opens pre-filtered on
  // FAILED (the missed ones), but the filter is a regular removable chip so
  // the user can widen the view to every expectation.
  const MISSED_COLUMNS = ['inject_title', 'inject_expectation_source', 'inject_expectation_type', 'inject_expectation_status', 'inject_expectation_score', 'base_created_at'];
  const [missedLoading, setMissedLoading] = useState(true);
  // Static key (like 'asset-injects' & co): one shared entry instead of an
  // unbounded localStorage entry per platform ever visited.
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'security-platform-expectations',
    buildSearchPagination({
      sorts: initSorting('base_created_at', 'DESC'),
      filterGroup: {
        mode: 'and',
        filters: [buildFilter('inject_expectation_status', ['FAILED'], 'eq')],
      },
    }),
  );

  const missedInlineStyles: Record<string, CSSProperties> = {
    inject_title: { width: '26%' },
    inject_expectation_source: { width: '16%' },
    inject_expectation_type: { width: '13%' },
    inject_expectation_status: { width: '17%' },
    inject_expectation_score: { width: '10%' },
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
      field: 'inject_expectation_source',
      // The target the expectation was evaluated against (endpoint, asset
      // group, team, player) - same resolved-name chip as the findings and
      // dashboard expectation lists.
      label: 'Source',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => <InjectExpectationSourceFragment element={expectation as unknown as EsBase} />,
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
        // Same score pill as the exposure validation cards: status-colored
        // tint with the obtained score prominent and the expected score dimmed.
        // A null side means "not scored" - show a dash rather than a fake 0.
        const statusColor = computeStatusStyle(expectation.inject_expectation_status).color;
        return (
          <Box
            component="span"
            sx={{
              minWidth: 34,
              height: 22,
              borderRadius: 1,
              paddingInline: 0.75,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 0.5,
              backgroundColor: alpha(statusColor, 0.12),
              color: statusColor,
              fontSize: 12,
              fontWeight: 700,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {score != null ? Math.round(score) : '-'}
            {expected != null && (
              <Box
                component="span"
                sx={{
                  opacity: 0.6,
                  fontWeight: 600,
                }}
              >
                {`/ ${Math.round(expected)}`}
              </Box>
            )}
          </Box>
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
        // No hard-coded status here: the FAILED scope is a regular runtime
        // filter (seeded as a default chip) that the user can remove.
        filter: group(
          filter('base_entity', ['expectation-inject']),
          platformFilter,
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
    const pillar = (key: string) => posture.breakdown.find(entry => entry.key === key);
    const detection = pillar('DETECTION');
    const prevention = pillar('PREVENTION');
    return {
      detectionRate: rate(detection?.success ?? 0, detection?.failed ?? 0),
      preventionRate: rate(prevention?.success ?? 0, prevention?.failed ?? 0),
      tested: posture.tested,
      missedCount: posture.failed,
    };
  }, [posture]);

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
            disabled={isCollectorManaged(platform)}
          />
        )}
        stats={(
          <>
            {/* Rates and counters stay neutral (primary/secondary): a green 0%
                or an orange counter would carry a false verdict - the verdict
                lives in the posture score, whose color follows the number. */}
            <HeroStat
              icon={ShieldOutlined}
              label={t('Prevention rate')}
              value={kpis.preventionRate === null ? '-' : `${kpis.preventionRate}%`}
              color={theme.palette.primary.main}
            />
            <HeroStat
              icon={GppMaybeOutlined}
              label={t('Detection rate')}
              value={kpis.detectionRate === null ? '-' : `${kpis.detectionRate}%`}
              color={theme.palette.secondary.main}
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
            />
            <PostureScore
              scope="security-platform"
              success={posture.success}
              failed={posture.failed}
              breakdown={posture.breakdown}
              loading={posture.loading}
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
        <SectionBlock title={t('Posture score over time')}>
          {/* Same SUCCESS / FAILED ratio as the hero posture score, plotted
              weekly - shared chart with the asset & asset group Statistics tabs. */}
          <PostureScoreOverTimeChart
            scopeField={PLATFORM_FILTER_KEY}
            entityId={securityPlatformId}
            scopeOperator="contains"
            height={280}
          />
        </SectionBlock>
      </DetailSections>

      <div>
        <SectionLabel>{t('Latest expectations')}</SectionLabel>
        <PaginationComponentV2
          fetch={fetchMissed}
          searchPaginationInput={searchPaginationInput}
          setContent={setMissed}
          // ES-backed list: filter properties come from the engine schema (the
          // JPA InjectExpectation schema does not expose the computed status).
          engineEntityName="expectation-inject"
          availableFilterNames={['inject_expectation_type', 'inject_expectation_status', 'inject_expectation_score']}
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
                // Real router link (not a JS navigate) so ctrl/cmd+click opens a new
                // tab; rows without a resolvable target stay non-navigable.
                const url = getNavigationUrl(element);
                return (
                  <ListItem
                    key={expectation.base_id}
                    divider
                    disablePadding
                    secondaryAction={url ? <KeyboardArrowRight color="action" /> : <>&nbsp;</>}
                  >
                    <ListItemButton
                      style={{ height: 50 }}
                      {...(url
                        ? {
                            component: Link,
                            to: url,
                          }
                        : { disabled: true })}
                      sx={url
                        ? undefined
                        : {
                            '&.Mui-disabled': { opacity: 1 },
                            'cursor': 'default',
                          }}
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
