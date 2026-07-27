import { type Reporting, type ReportingModule } from '../../../../utils/api-types';

/**
 * Default (english, i18n-key) labels of the render page. All values go through
 * useFormatter's t() at render time; english is the accepted fallback until the
 * locale batching phase.
 */

export const TIME_RANGE_LABELS: Record<Reporting['reporting_time_range'], string> = {
  LAST_7_DAYS: 'Last 7 days',
  LAST_30_DAYS: 'Last 30 days',
  LAST_90_DAYS: 'Last 90 days',
  LAST_180_DAYS: 'Last 180 days',
  LAST_365_DAYS: 'Last 365 days',
  ALL_TIME: 'All time',
};

export const MODULE_TYPE_LABELS: Record<ReportingModule['module_type'], string> = {
  COVER: 'Cover',
  EXECUTIVE_SUMMARY: 'Executive summary',
  SUBJECT_DETAILS: 'Subject details',
  // The platform is multi-kill-chain (ATT&CK, ATLAS, custom...): the module
  // covers whichever kill chains its module_config selects, so the default
  // title stays framework-agnostic. The enum value is historical.
  MITRE_COVERAGE: 'Kill chain coverage',
  RESULTS_BREAKDOWN: 'Results breakdown',
  SECURITY_DOMAINS: 'Performance by security domain',
  SCORE_TRENDS: 'Score trends',
  FAILED_EXPECTATIONS: 'Failed expectations',
  FINDINGS: 'Findings',
  ATTACK_PATHS: 'Attack paths',
  CUSTOM_MARKDOWN: 'Custom content',
};

/** Effective section title: user override first, then the type default. */
export const moduleTitle = (
  module: ReportingModule,
  t: (key: string) => string,
): string => module.module_title || t(MODULE_TYPE_LABELS[module.module_type]);
