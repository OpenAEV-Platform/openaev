import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { type Filter, type FilterGroup, type Series, type Widget } from '../../../utils/api-types';

/**
 * Hardcoded "Platform default" home dashboard definition.
 *
 * The widgets below are plain Widget objects with synthetic ids: they reuse the
 * whole custom dashboard rendering engine (grid, wrappers, visualizations) but
 * are never persisted. Their data is fetched through the ad-hoc dashboard API
 * by sending the full configuration. Selecting a custom dashboard in the
 * tenant parameters fully replaces this dashboard; clearing the selection
 * rolls back to it.
 */

export const PLATFORM_DEFAULT_DASHBOARD_ID = '_platform_default_home';

/**
 * Terms-aggregation bucket cap for the MITRE coverage widget. The default cap
 * (100) silently truncates the per-technique series on busy platforms (600+
 * technique/sub-technique ids), making tiles show "perfect" scores while the
 * drill-down list disagrees. Mirrors COVERAGE_BUCKET_CAP in AttackPatternService.
 */
const COVERAGE_BUCKET_CAP = 10000;

export type DefaultTimeRange = 'ALL_TIME' | 'LAST_DAY' | 'LAST_WEEK' | 'LAST_MONTH' | 'LAST_QUARTER' | 'LAST_SEMESTER' | 'LAST_YEAR';

/**
 * Window sizes in hours, mirroring the backend's
 * CustomDashboardQueryUtils.calcStartDate so a materialized date filter agrees
 * with what the widget tiles aggregated.
 */
const TIME_RANGE_HOURS: Record<Exclude<DefaultTimeRange, 'ALL_TIME'>, number> = {
  LAST_DAY: 24,
  LAST_WEEK: 7 * 24,
  LAST_MONTH: 30 * 24,
  LAST_QUARTER: 90 * 24,
  LAST_SEMESTER: 180 * 24,
  LAST_YEAR: 360 * 24,
};

/** ISO start instant of a dashboard time range, or null for ALL_TIME (no lower bound). */
export const timeRangeStartDate = (timeRange: DefaultTimeRange): string | null => {
  if (timeRange === 'ALL_TIME') {
    return null;
  }
  return new Date(Date.now() - TIME_RANGE_HOURS[timeRange] * 3_600_000).toISOString();
};

/** Translator signature: widget titles are localized at build time (see buildDefaultHomeWidgets). */
type Translate = (key: string) => string;

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

const expectation = (extra: Filter[] = []) => [filter('base_entity', ['expectation-inject']), ...extra];

const layout = (x: number, y: number, w: number, h: number): Widget['widget_layout'] => ({
  widget_layout_x: x,
  widget_layout_y: y,
  widget_layout_w: w,
  widget_layout_h: h,
});

const NOW = new Date().toISOString();

const widget = (
  id: string,
  type: Widget['widget_type'],
  config: Widget['widget_config'],
  widgetLayout: Widget['widget_layout'],
): Widget => ({
  widget_id: id,
  widget_type: type,
  widget_config: config,
  widget_layout: widgetLayout,
  widget_created_at: NOW,
  widget_updated_at: NOW,
});

const structural = (
  title: string,
  field: string,
  seriesList: Series[],
  timeRange: DefaultTimeRange,
  limit = 10,
): Widget['widget_config'] => ({
  title,
  field,
  series: seriesList,
  mode: 'structural',
  stacked: false,
  limit,
  widget_configuration_type: 'structural-histogram',
  time_range: timeRange,
  date_attribute: 'base_created_at',
  display_legend: false,
});

const temporal = (
  title: string,
  seriesList: Series[],
  timeRange: DefaultTimeRange,
  interval: 'day' | 'week' | 'month' = 'week',
): Widget['widget_config'] => ({
  title,
  series: seriesList,
  mode: 'temporal',
  stacked: false,
  interval,
  widget_configuration_type: 'temporal-histogram',
  time_range: timeRange,
  date_attribute: 'base_created_at',
  display_legend: false,
});

const flat = (
  title: string,
  seriesList: Series[],
  timeRange: DefaultTimeRange,
): Widget['widget_config'] => ({
  title,
  series: seriesList,
  widget_configuration_type: 'flat',
  time_range: timeRange,
  date_attribute: 'base_created_at',
});

const list = (
  title: string,
  perspective: Series,
  columns: string[],
  timeRange: DefaultTimeRange,
): Widget['widget_config'] => ({
  title,
  series: [],
  perspective,
  columns,
  sorts: [{
    fieldName: 'base_created_at',
    direction: 'DESC',
  }],
  limit: 100,
  widget_configuration_type: 'list',
  time_range: timeRange,
  date_attribute: 'base_created_at',
});

const average = (title: string, timeRange: DefaultTimeRange): Widget['widget_config'] => ({
  title,
  series: [series('', filter('base_entity', ['expectation-inject']))],
  widget_configuration_type: 'average',
  time_range: timeRange,
  date_attribute: 'base_created_at',
});

const successFailedSeries = (extra: Filter[] = []): Series[] => [
  series('SUCCESS', ...expectation([filter('inject_expectation_status', ['SUCCESS']), ...extra])),
  series('FAILED', ...expectation([filter('inject_expectation_status', ['FAILED']), ...extra])),
];

/**
 * SUCCESS / FAILED / PENDING, in that order - the command center's ADVERSARY node
 * counts every attempted validation, so the still-unscored ones must be part of the
 * widget rather than reconstructed at click time. The index order is contractual:
 * the drill-downs name the series they aggregated (see CommandCenterWidget).
 */
const attemptedSeries = (): Series[] => [
  ...successFailedSeries(),
  series('PENDING', ...expectation([filter('inject_expectation_status', ['PENDING'])])),
];

/**
 * Human-driven expectation types (analyst validation, media pressure, challenges).
 * "Human Response" aggregates these - the same MANUAL/ARTICLE/CHALLENGE gate the
 * exposure command center surfaces as its human node.
 */
export const HUMAN_RESPONSE_EXPECTATION_TYPES = ['MANUAL', 'ARTICLE', 'CHALLENGE'];

/**
 * The exact widget config the "Human Response" gauge queries, reused as a
 * presence probe: the dashboard fetches it once and only mounts the gauge when
 * at least one human-driven expectation exists in range (mirrors the command
 * center hiding its human node when there is nothing to show). The title is
 * irrelevant to the probe (the filters drive the query), so it is left untranslated.
 */
export const buildHumanResponseProbeConfig = (timeRange: DefaultTimeRange): Widget['widget_config'] =>
  structural('Human Response', 'inject_expectation_status', [
    series('', ...expectation([filter('inject_expectation_type', HUMAN_RESPONSE_EXPECTATION_TYPES)])),
  ], timeRange, 100);

/**
 * @param includeHumanResponse whether the "Human Response" gauge is mounted. It is
 *   only shown when human-driven expectations exist in range (see
 *   {@link buildHumanResponseProbeConfig}). The gauge row is fully responsive: the
 *   gauges always divide the 12-column row evenly, so three gauges are w=4 (no gap)
 *   and four gauges are w=3 (Human Response on the SAME line). The caller MUST
 *   remount the grid when this flag flips (see DefaultHomeDashboard) - react-grid-
 *   layout keeps the internal layout of already-mounted children and ignores a
 *   width change, so an in-place flip would leave the core gauges at their old
 *   width and strand Human Response on a second row. To keep every load
 *   single-mount, the caller defers the FIRST grid mount until the presence
 *   probe has resolved instead of remounting on the fly (#7599).
 */
export const buildDefaultHomeWidgets = (
  timeRange: DefaultTimeRange,
  t: Translate = key => key,
  includeHumanResponse = false,
): Widget[] => {
  // -- EXPECTATION RESULTS --
  // Resilience donuts dividing the 12-column row evenly. The three core
  // security-control gauges are always shown; "Human Response" (MANUAL/ARTICLE/
  // CHALLENGE, e.g. phishing) is appended only when it has data. Three gauges fill
  // the row at w=4; four fill it at w=3. No empty slot in either state.
  const gaugeDefs: {
    id: string;
    title: string;
    types: string[];
  }[] = [
    {
      id: 'default-prevention',
      title: 'Prevention',
      types: ['PREVENTION'],
    },
    {
      id: 'default-detection',
      title: 'Detection',
      types: ['DETECTION'],
    },
    {
      id: 'default-vulnerability',
      title: 'Vulnerability',
      types: ['VULNERABILITY'],
    },
  ];
  if (includeHumanResponse) {
    gaugeDefs.push({
      id: 'default-human-response',
      title: 'Human Response',
      types: HUMAN_RESPONSE_EXPECTATION_TYPES,
    });
  }
  // Evenly divide the 12-column row: 3 gauges -> w=4, 4 gauges -> w=3.
  const gaugeWidth = 12 / gaugeDefs.length;
  const gaugeWidgets = gaugeDefs.map((gauge, index) => widget(
    gauge.id,
    'resilience-gauge',
    structural(t(gauge.title), 'inject_expectation_status', [
      series('', ...expectation([filter('inject_expectation_type', gauge.types)])),
    ], timeRange, 100),
    layout(index * gaugeWidth, 6, gaugeWidth, 4),
  ));

  return [
  // -- HERO: exposure command center --
    widget(
      'default-command-center',
      'command-center',
      structural(t('Exposure command center'), 'inject_expectation_type', attemptedSeries(), timeRange),
      layout(0, 0, 12, 6),
    ),

    ...gaugeWidgets,

    // -- PERFORMANCE BY SECURITY DOMAIN (full-width band) --
    widget(
      'default-security-domains',
      'average',
      average(t('Performance by security domain'), timeRange),
      layout(0, 10, 12, 4),
    ),

    // -- MITRE TTP POSTURE (detection coverage matrix, full-width band) --
    widget(
      'default-detection-coverage',
      'security-coverage',
      structural(t('Detection coverage'), 'base_attack_patterns_side', [
        series('SUCCESS', ...expectation([
          filter('inject_expectation_status', ['SUCCESS']),
          filter('inject_expectation_type', ['DETECTION']),
        ])),
        series('FAILED', ...expectation([
          filter('inject_expectation_status', ['FAILED']),
          filter('inject_expectation_type', ['DETECTION']),
        ])),
      ], timeRange, COVERAGE_BUCKET_CAP),
      layout(0, 14, 12, 10),
    ),

    // -- POSTURE + KPIs + FINDINGS BREAKDOWN --
    widget(
      'default-posture-radar',
      'posture-radar',
      structural(t('Posture radar'), 'base_security_platforms_side', successFailedSeries(), timeRange),
      layout(0, 24, 4, 6),
    ),
    widget(
      'default-kpi-scenarios',
      'number',
      flat(t('Scenarios'), [series('Scenario', filter('base_entity', ['scenario']))], timeRange),
      layout(4, 24, 2, 2),
    ),
    widget(
      'default-kpi-simulations',
      'number',
      flat(t('Simulations'), [series('Simulation', filter('base_entity', ['simulation']))], timeRange),
      layout(6, 24, 2, 2),
    ),
    widget(
      'default-kpi-injects',
      'number',
      flat(t('Injects'), [series('Inject', filter('base_entity', ['inject']))], timeRange),
      layout(4, 26, 2, 2),
    ),
    widget(
      'default-kpi-assets',
      'number',
      flat(t('Assets'), [series('Asset', filter('base_entity', ['asset']))], timeRange),
      layout(6, 26, 2, 2),
    ),
    widget(
      'default-kpi-cves',
      'number',
      flat(t('CVEs found'), [series('CVE', filter('base_entity', ['finding']), filter('finding_type', ['CVE']))], timeRange),
      layout(4, 28, 2, 2),
    ),
    widget(
      'default-kpi-vulnerable-endpoints',
      'number',
      flat(t('Vulnerable endpoints'), [series('Vulnerable endpoint', filter('base_entity', ['vulnerable-endpoint']))], timeRange),
      layout(6, 28, 2, 2),
    ),
    widget(
      'default-latest-findings',
      'horizontal-barchart',
      structural(t('Findings by type'), 'finding_type', [
        series('', filter('base_entity', ['finding'])),
      ], timeRange, 100),
      layout(8, 24, 4, 6),
    ),

    // -- FINDINGS --
    widget(
      'default-findings-list',
      'list',
      list(
        t('Latest findings'),
        series('', filter('base_entity', ['finding'])),
        ['finding_value', 'base_created_at', 'finding_type'],
        timeRange,
      ),
      layout(0, 30, 8, 10),
    ),
    widget(
      'default-kpi-total-findings',
      'number',
      flat(t('Total findings'), [series('Finding', filter('base_entity', ['finding']))], timeRange),
      layout(8, 30, 2, 2),
    ),
    widget(
      'default-kpi-ports-open',
      'number',
      flat(t('Ports open'), [series('Port', filter('base_entity', ['finding']), filter('finding_type', ['PortsScan', 'Port']))], timeRange),
      layout(10, 30, 2, 2),
    ),
    widget(
      'default-undetected-platforms',
      'vertical-barchart',
      structural(t('Missed by security platform'), 'base_security_platforms_side', [
        series('Not Detected', ...expectation([
          filter('inject_expectation_type', ['DETECTION']),
          filter('inject_expectation_status', ['FAILED']),
        ])),
        series('Not Prevented', ...expectation([
          filter('inject_expectation_type', ['PREVENTION']),
          filter('inject_expectation_status', ['FAILED']),
        ])),
      ], timeRange, 100),
      layout(8, 32, 4, 8),
    ),

    // -- TRENDS --
    widget(
      'default-weekly-failures',
      'vertical-barchart',
      // eq FAILED (not "not_eq SUCCESS"): pending/unknown expectations are not misses.
      temporal(t('Missed injects by week'), [
        series('Not Detected', ...expectation([
          filter('inject_expectation_type', ['DETECTION']),
          filter('inject_expectation_status', ['FAILED']),
        ])),
        series('Not Prevented', ...expectation([
          filter('inject_expectation_type', ['PREVENTION']),
          filter('inject_expectation_status', ['FAILED']),
        ])),
      ], timeRange),
      layout(0, 40, 4, 6),
    ),
    widget(
      'default-top-detected-ttps',
      'horizontal-barchart',
      structural(t('Most detected & prevented TTPs'), 'base_attack_patterns_side', [
        series('Detected TTPs', ...expectation([
          filter('inject_expectation_type', ['DETECTION']),
          filter('inject_expectation_status', ['SUCCESS']),
        ])),
        series('Prevented TTPs', ...expectation([
          filter('inject_expectation_type', ['PREVENTION']),
          filter('inject_expectation_status', ['SUCCESS']),
        ])),
      ], timeRange),
      layout(4, 40, 4, 6),
    ),
    widget(
      'default-top-undetected-ttps',
      'horizontal-barchart',
      structural(t('Most undetected TTPs'), 'base_attack_patterns_side', [
        series('Undetected TTPs', ...expectation([
          filter('inject_expectation_type', ['DETECTION']),
          filter('inject_expectation_status', ['FAILED']),
        ])),
        series('Unprevented TTPs', ...expectation([
          filter('inject_expectation_type', ['PREVENTION']),
          filter('inject_expectation_status', ['FAILED']),
        ])),
      ], timeRange),
      layout(8, 40, 4, 6),
    ),
    widget(
      'default-simulations-by-week',
      'line',
      temporal(t('Simulations by week'), [
        series('Simulation', filter('base_entity', ['simulation'])),
      ], timeRange),
      layout(0, 46, 6, 6),
    ),
    widget(
      'default-latest-simulations',
      'list',
      list(
        t('Latest simulations'),
        series('', filter('base_entity', ['simulation'])),
        ['name', 'status', 'base_created_at', 'base_tags_side'],
        timeRange,
      ),
      layout(6, 46, 6, 6),
    ),
  ];
};
