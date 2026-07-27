import { searchAssetGroupAsOption, searchAssetGroupByIdAsOption } from '../../../actions/asset_groups/assetgroup-action';
import { searchEndpointAsOption, searchEndpointByIdAsOption } from '../../../actions/assets/endpoint-actions';
import { searchAtomicTestings } from '../../../actions/atomic_testings/atomic-testing-actions';
import { searchInjectByIdAsOption } from '../../../actions/injects/inject-action';
import { searchScenarioAsOption, searchScenarioByIdAsOption } from '../../../actions/scenarios/scenario-actions';
import { searchSimulationAsOptions, searchSimulationByIdAsOptions } from '../../../actions/simulations/simulation-action';
import { searchTeamByIdAsOption, searchTeamsAsOption } from '../../../actions/teams/team-actions';
import { searchPlayerByIdAsOption, searchPlayersAsOption } from '../../../actions/users/User';
import { initSorting, type Page } from '../../../components/common/queryable/Page';
import { type InjectResultOutput, type Reporting, type ReportingModule, type ThemeInput } from '../../../utils/api-types';
import { type Option } from '../../../utils/Option';

export type ReportingContextType = Reporting['reporting_context_type'];
export type ReportingModuleType = ReportingModule['module_type'];
export type ReportingTimeRange = Reporting['reporting_time_range'];
export type ReportingFormat = Reporting['reporting_default_format'];
export type ReportingThemeMode = 'LIGHT' | 'DARK';

// -- ENUM VALUE LISTS (zod schemas + selects) --------------------------------

export const REPORTING_CONTEXT_TYPES = [
  'PLATFORM',
  'SIMULATION',
  'SCENARIO',
  'ATOMIC_TESTING',
  'ENDPOINT',
  'ASSET_GROUP',
  'PLAYER',
  'TEAM',
] as const;

export const REPORTING_TIME_RANGES = [
  'LAST_7_DAYS',
  'LAST_30_DAYS',
  'LAST_90_DAYS',
  'LAST_180_DAYS',
  'LAST_365_DAYS',
  'ALL_TIME',
] as const;

export const REPORTING_FORMATS = ['PDF', 'HTML'] as const;

export const REPORTING_SCHEDULE_PERIODS = ['HOUR', 'DAY', 'WEEK', 'MONTH'] as const;

export const REPORTING_MODULE_TYPES = [
  'COVER',
  'EXECUTIVE_SUMMARY',
  'SUBJECT_DETAILS',
  'MITRE_COVERAGE',
  'RESULTS_BREAKDOWN',
  'SECURITY_DOMAINS',
  'SCORE_TRENDS',
  'FAILED_EXPECTATIONS',
  'FINDINGS',
  'ATTACK_PATHS',
  'CUSTOM_MARKDOWN',
] as const;

/** Default module composition of a new report (reading order). */
export const DEFAULT_MODULE_TYPES: ReportingModuleType[] = [
  'COVER',
  'EXECUTIVE_SUMMARY',
  'SUBJECT_DETAILS',
  'MITRE_COVERAGE',
  'RESULTS_BREAKDOWN',
  'SECURITY_DOMAINS',
  'SCORE_TRENDS',
  'FAILED_EXPECTATIONS',
  'FINDINGS',
];

// i18n keys - translated through useFormatter's t()
export const MODULE_TYPE_DESCRIPTIONS: Record<ReportingModuleType, string> = {
  COVER: 'Title page with the report name, subject and generation date.',
  EXECUTIVE_SUMMARY: 'High-level posture summary with the key figures of the period.',
  SUBJECT_DETAILS: 'Detailed information about the report subject.',
  MITRE_COVERAGE: 'Techniques exercised over the period, for one or all kill chains.',
  RESULTS_BREAKDOWN: 'Success and failure breakdown by expectation type.',
  SECURITY_DOMAINS: 'Success rate per security domain (endpoint, network, web app...).',
  SCORE_TRENDS: 'Evolution of the results over time.',
  FAILED_EXPECTATIONS: 'Most recent failed expectations of the period.',
  FINDINGS: 'Findings collected over the period, by type and most recent.',
  ATTACK_PATHS: 'Kill chain phase progression of the executed injects.',
  CUSTOM_MARKDOWN: 'Free-form content written in markdown.',
};

// i18n keys - translated through useFormatter's t()
export const SCHEDULE_PERIOD_LABELS: Record<(typeof REPORTING_SCHEDULE_PERIODS)[number], string> = {
  HOUR: 'Every hour',
  DAY: 'Every day',
  WEEK: 'Every week',
  MONTH: 'Every month',
};

/** ISO day-of-week i18n labels, index 0 = ISO day 1 (Monday). */
export const WEEKDAY_LABELS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

// -- BRANDING SEED ------------------------------------------------------------

export interface BrandingColorSeed {
  primary_color: string;
  secondary_color: string;
  accent_color: string;
  background_color: string;
  paper_color: string;
  text_color: string;
}

// Mirror of the default palette constants in ThemeLight.ts / ThemeDark.ts
// (most are not exported there). The seed makes the implicit platform default
// branding explicit and editable in the wizard.
const LIGHT_FALLBACK: BrandingColorSeed = {
  primary_color: '#0015a8',
  secondary_color: '#00BD94',
  accent_color: '#dfdfdf',
  background_color: '#ececf2',
  paper_color: '#ffffff',
  text_color: '#18191B',
};

const DARK_FALLBACK: BrandingColorSeed = {
  primary_color: '#0fbcff',
  secondary_color: '#00f18d',
  accent_color: '#0f1e38',
  background_color: '#070d19',
  paper_color: '#09101e',
  text_color: '#F2F2F3',
};

/**
 * Six branding colors resolved the same way the render page resolves them:
 * custom platform theme colors first, hardcoded theme defaults otherwise.
 * The platform theme has no text color setting: the theme default is used.
 */
export const platformBrandingSeed = (
  mode: ReportingThemeMode,
  themeConfig?: ThemeInput,
): BrandingColorSeed => {
  const fallback = mode === 'LIGHT' ? LIGHT_FALLBACK : DARK_FALLBACK;
  return {
    primary_color: themeConfig?.primary_color || fallback.primary_color,
    secondary_color: themeConfig?.secondary_color || fallback.secondary_color,
    accent_color: themeConfig?.accent_color || fallback.accent_color,
    background_color: themeConfig?.background_color || fallback.background_color,
    paper_color: themeConfig?.paper_color || fallback.paper_color,
    text_color: fallback.text_color,
  };
};

// -- SUBJECT ENTITY OPTIONS ----------------------------------------------------

const mapAtomicTestingOptions = (page: Page<InjectResultOutput>): Option[] =>
  (page.content ?? []).map(inject => ({
    id: inject.inject_id,
    label: inject.inject_title || inject.inject_id,
  }));

/** Text-search the candidate subject entities of a context type. */
export const searchSubjectOptions = (
  contextType: ReportingContextType,
  search: string = '',
): Promise<Option[]> => {
  switch (contextType) {
    case 'SIMULATION':
      return searchSimulationAsOptions(search).then((result: { data: Option[] }) => result.data);
    case 'SCENARIO':
      return searchScenarioAsOption(search).then((result: { data: Option[] }) => result.data);
    case 'ATOMIC_TESTING':
      // Atomic testings have no lightweight /options endpoint: page through the
      // regular search and map the results to options.
      return searchAtomicTestings({
        page: 0,
        size: 50,
        textSearch: search,
        sorts: initSorting('inject_updated_at', 'DESC'),
      }).then((result: { data: Page<InjectResultOutput> }) => mapAtomicTestingOptions(result.data));
    case 'ENDPOINT':
      return searchEndpointAsOption(search).then((result: { data: Option[] }) => result.data);
    case 'ASSET_GROUP':
      return searchAssetGroupAsOption(search).then((result: { data: Option[] }) => result.data);
    case 'PLAYER':
      return searchPlayersAsOption(search).then((result: { data: Option[] }) => result.data);
    case 'TEAM':
      return searchTeamsAsOption(search).then((result: { data: Option[] }) => result.data);
    default:
      return Promise.resolve([]);
  }
};

/** Resolve subject entity ids into display options (edit mode, detail header). */
export const resolveSubjectOptions = (
  contextType: ReportingContextType,
  ids: string[],
): Promise<Option[]> => {
  if (ids.length === 0) return Promise.resolve([]);
  let request: Promise<{ data: Option[] }> | undefined;
  switch (contextType) {
    case 'SIMULATION':
      request = searchSimulationByIdAsOptions(ids);
      break;
    case 'SCENARIO':
      request = searchScenarioByIdAsOption(ids);
      break;
    case 'ATOMIC_TESTING':
      request = searchInjectByIdAsOption(ids);
      break;
    case 'ENDPOINT':
      request = searchEndpointByIdAsOption(ids);
      break;
    case 'ASSET_GROUP':
      request = searchAssetGroupByIdAsOption(ids);
      break;
    case 'PLAYER':
      request = searchPlayerByIdAsOption(ids);
      break;
    case 'TEAM':
      request = searchTeamByIdAsOption(ids);
      break;
    default:
      return Promise.resolve([]);
  }
  return request.then(result => result.data).catch(() => []);
};
