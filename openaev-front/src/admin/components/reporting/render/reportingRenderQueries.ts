import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { type Filter, type FilterGroup, type Reporting, type Series, type Widget } from '../../../../utils/api-types';

/**
 * Pure builders for the ad-hoc dashboard-engine queries powering the reporting
 * render page. Everything here is a plain function so the data hook
 * (useReportingRenderData) stays a thin orchestration layer.
 */

export type ReportingContextType = Reporting['reporting_context_type'];
export type ReportingTimeRange = Reporting['reporting_time_range'];

/** Widget-engine time range enum (subset used by reporting). */
type EngineTimeRange = 'ALL_TIME' | 'LAST_WEEK' | 'LAST_MONTH' | 'LAST_QUARTER' | 'LAST_SEMESTER' | 'LAST_YEAR';

/**
 * Reporting time ranges map onto the closest dashboard-engine window. The
 * engine's LAST_YEAR is a 360-day window (see CustomDashboardQueryUtils on the
 * backend), an acceptable 5-day drift for LAST_365_DAYS.
 */
export const toEngineTimeRange = (range: ReportingTimeRange): EngineTimeRange => {
  switch (range) {
    case 'LAST_7_DAYS': return 'LAST_WEEK';
    case 'LAST_30_DAYS': return 'LAST_MONTH';
    case 'LAST_90_DAYS': return 'LAST_QUARTER';
    case 'LAST_180_DAYS': return 'LAST_SEMESTER';
    case 'LAST_365_DAYS': return 'LAST_YEAR';
    default: return 'ALL_TIME';
  }
};

/** Temporal bucketing interval adapted to the report window. */
export const toTrendInterval = (range: ReportingTimeRange): 'day' | 'week' | 'month' => {
  switch (range) {
    case 'LAST_7_DAYS':
    case 'LAST_30_DAYS':
      return 'day';
    case 'LAST_90_DAYS':
    case 'LAST_180_DAYS':
      return 'week';
    default:
      return 'month';
  }
};

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

const series = (name: string, ...filters: Filter[]): Series => ({
  name,
  filter: group(...filters),
});

/**
 * ES side field carrying the reporting scope on expectation-inject documents.
 * PLATFORM is unscoped (null field, empty filter list).
 */
const EXPECTATION_SCOPE_FIELD: Record<ReportingContextType, string | null> = {
  PLATFORM: null,
  SIMULATION: 'base_simulation_side',
  SCENARIO: 'base_scenario_side',
  ATOMIC_TESTING: 'base_inject_side',
  ENDPOINT: 'base_asset_side',
  ASSET_GROUP: 'base_asset_group_side',
  PLAYER: 'base_user_side',
  TEAM: 'base_team_side',
};

/**
 * ES side field carrying the reporting scope on inject documents. Injects are
 * not indexed per player, so PLAYER is undefined (module falls back to an
 * expectation-based figure or an empty state).
 */
const INJECT_SCOPE_FIELD: Record<ReportingContextType, string | null | undefined> = {
  PLATFORM: null,
  SIMULATION: 'base_simulation_side',
  SCENARIO: 'base_scenario_side',
  ATOMIC_TESTING: 'base_id',
  ENDPOINT: 'base_assets_side',
  ASSET_GROUP: 'base_asset_groups_side',
  PLAYER: undefined,
  TEAM: 'base_teams_side',
};

/**
 * ES side field carrying the reporting scope on finding documents. Findings
 * are attached to injects/endpoints only: player, team and asset-group scopes
 * are undefined (FINDINGS module renders its empty state there).
 */
const FINDING_SCOPE_FIELD: Record<ReportingContextType, string | null | undefined> = {
  PLATFORM: null,
  SIMULATION: 'base_simulation_side',
  SCENARIO: 'base_scenario_side',
  ATOMIC_TESTING: 'base_inject_side',
  ENDPOINT: 'base_endpoint_side',
  ASSET_GROUP: undefined,
  PLAYER: undefined,
  TEAM: undefined,
};

type ScopeKind = 'expectation' | 'inject' | 'finding';

const SCOPE_FIELDS: Record<ScopeKind, Record<ReportingContextType, string | null | undefined>> = {
  expectation: EXPECTATION_SCOPE_FIELD,
  inject: INJECT_SCOPE_FIELD,
  finding: FINDING_SCOPE_FIELD,
};

/** True when the entity kind can be scoped to the given reporting context. */
export const isScopeSupported = (kind: ScopeKind, contextType: ReportingContextType): boolean =>
  SCOPE_FIELDS[kind][contextType] !== undefined;

/** Scope filters for an entity kind (empty for PLATFORM). */
const scopeFilters = (kind: ScopeKind, contextType: ReportingContextType, contextId?: string): Filter[] => {
  const field = SCOPE_FIELDS[kind][contextType];
  if (!field || !contextId) return [];
  return [filter(field, [contextId])];
};

const expectationFilters = (contextType: ReportingContextType, contextId?: string, extra: Filter[] = []): Filter[] => [
  filter('base_entity', ['expectation-inject']),
  ...scopeFilters('expectation', contextType, contextId),
  ...extra,
];

interface StructuralParams {
  field: string;
  seriesList: Series[];
  timeRange: ReportingTimeRange;
  limit?: number;
}

const structuralConfig = ({ field, seriesList, timeRange, limit = 10 }: StructuralParams): Widget['widget_config'] => ({
  title: '',
  field,
  series: seriesList,
  mode: 'structural',
  stacked: false,
  limit,
  widget_configuration_type: 'structural-histogram',
  time_range: toEngineTimeRange(timeRange),
  date_attribute: 'base_created_at',
  display_legend: false,
} as unknown as Widget['widget_config']);

const temporalConfig = (seriesList: Series[], timeRange: ReportingTimeRange): Widget['widget_config'] => ({
  title: '',
  series: seriesList,
  mode: 'temporal',
  stacked: false,
  interval: toTrendInterval(timeRange),
  widget_configuration_type: 'temporal-histogram',
  time_range: toEngineTimeRange(timeRange),
  date_attribute: 'base_created_at',
  display_legend: false,
} as unknown as Widget['widget_config']);

/** Every expectation type the platform validates (mirrors the backend enum). */
export const EXPECTATION_TYPES = ['PREVENTION', 'DETECTION', 'VULNERABILITY', 'MANUAL', 'ARTICLE', 'CHALLENGE'] as const;

/**
 * Posture query: per expectation type x validation status counts, feeding the
 * executive summary KPIs and the results breakdown module.
 */
export const buildPostureConfig = (
  contextType: ReportingContextType,
  contextId: string | undefined,
  timeRange: ReportingTimeRange,
): Widget['widget_config'] => structuralConfig({
  field: 'inject_expectation_type',
  seriesList: EXPECTATION_TYPES.flatMap(type => (['SUCCESS', 'FAILED'] as const).map(status => series(
    `${type}_${status}`,
    ...expectationFilters(contextType, contextId, [
      filter('inject_expectation_type', [type]),
      filter('inject_expectation_status', [status]),
    ]),
  ))),
  timeRange,
  limit: 100,
});

/** Injects executed count (flat widget resolved via the count endpoint). */
export const buildInjectCountConfig = (
  contextType: ReportingContextType,
  contextId: string | undefined,
  timeRange: ReportingTimeRange,
): Widget['widget_config'] | null => {
  if (!isScopeSupported('inject', contextType)) return null;
  return {
    title: '',
    series: [series('Inject', filter('base_entity', ['inject']), ...scopeFilters('inject', contextType, contextId))],
    widget_configuration_type: 'flat',
    time_range: toEngineTimeRange(timeRange),
    date_attribute: 'base_created_at',
  } as unknown as Widget['widget_config'];
};

/**
 * Terms-aggregation bucket cap for the MITRE coverage query (mirrors
 * COVERAGE_BUCKET_CAP in AttackPatternService: the default 100 truncates
 * per-technique series on busy platforms).
 */
const COVERAGE_BUCKET_CAP = 10000;

/** MITRE coverage: SUCCESS / FAILED expectation counts per attack pattern. */
export const buildMitreConfig = (
  contextType: ReportingContextType,
  contextId: string | undefined,
  timeRange: ReportingTimeRange,
): Widget['widget_config'] => structuralConfig({
  field: 'base_attack_patterns_side',
  seriesList: [
    series('SUCCESS', ...expectationFilters(contextType, contextId, [filter('inject_expectation_status', ['SUCCESS'])])),
    series('FAILED', ...expectationFilters(contextType, contextId, [filter('inject_expectation_status', ['FAILED'])])),
  ],
  timeRange,
  limit: COVERAGE_BUCKET_CAP,
});

/**
 * Performance by security domain: the engine's average query aggregates
 * expectation results per security domain and expectation type (same query as
 * the home dashboard's "Performance by security domain" band), scoped to the
 * reporting context through the standard expectation side filters.
 */
export const buildSecurityDomainsConfig = (
  contextType: ReportingContextType,
  contextId: string | undefined,
  timeRange: ReportingTimeRange,
): Widget['widget_config'] => ({
  title: '',
  series: [series('', ...expectationFilters(contextType, contextId))],
  widget_configuration_type: 'average',
  time_range: toEngineTimeRange(timeRange),
  date_attribute: 'base_created_at',
} as unknown as Widget['widget_config']);

/** Score trends: SUCCESS / FAILED validated expectations over time. */
export const buildTrendsConfig = (
  contextType: ReportingContextType,
  contextId: string | undefined,
  timeRange: ReportingTimeRange,
): Widget['widget_config'] => temporalConfig(
  [
    series('SUCCESS', ...expectationFilters(contextType, contextId, [filter('inject_expectation_status', ['SUCCESS'])])),
    series('FAILED', ...expectationFilters(contextType, contextId, [filter('inject_expectation_status', ['FAILED'])])),
  ],
  timeRange,
);

/** Latest failed expectations, resolved through the ad-hoc entities endpoint. */
export const buildFailedExpectationsConfig = (
  contextType: ReportingContextType,
  contextId: string | undefined,
  timeRange: ReportingTimeRange,
  limit = 15,
): Widget['widget_config'] => ({
  title: '',
  series: [],
  perspective: series('', ...expectationFilters(contextType, contextId, [filter('inject_expectation_status', ['FAILED'])])),
  columns: ['inject_title', 'inject_expectation_name', 'inject_expectation_type', 'base_created_at'],
  sorts: [{
    fieldName: 'base_created_at',
    direction: 'DESC',
  }],
  limit,
  widget_configuration_type: 'list',
  time_range: toEngineTimeRange(timeRange),
  date_attribute: 'base_created_at',
} as unknown as Widget['widget_config']);

/** Findings distribution by type. Null when the context cannot scope findings. */
export const buildFindingsByTypeConfig = (
  contextType: ReportingContextType,
  contextId: string | undefined,
  timeRange: ReportingTimeRange,
): Widget['widget_config'] | null => {
  if (!isScopeSupported('finding', contextType)) return null;
  return structuralConfig({
    field: 'finding_type',
    seriesList: [series('', filter('base_entity', ['finding']), ...scopeFilters('finding', contextType, contextId))],
    timeRange,
    limit: 100,
  });
};

/** Latest findings list. Null when the context cannot scope findings. */
export const buildLatestFindingsConfig = (
  contextType: ReportingContextType,
  contextId: string | undefined,
  timeRange: ReportingTimeRange,
  limit = 15,
): Widget['widget_config'] | null => {
  if (!isScopeSupported('finding', contextType)) return null;
  return {
    title: '',
    series: [],
    perspective: series('', filter('base_entity', ['finding']), ...scopeFilters('finding', contextType, contextId)),
    columns: ['finding_value', 'finding_type', 'base_created_at'],
    sorts: [{
      fieldName: 'base_created_at',
      direction: 'DESC',
    }],
    limit,
    widget_configuration_type: 'list',
    time_range: toEngineTimeRange(timeRange),
    date_attribute: 'base_created_at',
  } as unknown as Widget['widget_config'];
};

/**
 * Attack paths: executed injects bucketed by kill chain phase. Null when the
 * context cannot scope injects (player reports).
 */
export const buildAttackPathsConfig = (
  contextType: ReportingContextType,
  contextId: string | undefined,
  timeRange: ReportingTimeRange,
): Widget['widget_config'] | null => {
  if (!isScopeSupported('inject', contextType)) return null;
  return structuralConfig({
    field: 'base_kill_chain_phases_side',
    seriesList: [series('', filter('base_entity', ['inject']), ...scopeFilters('inject', contextType, contextId))],
    timeRange,
    limit: 100,
  });
};
