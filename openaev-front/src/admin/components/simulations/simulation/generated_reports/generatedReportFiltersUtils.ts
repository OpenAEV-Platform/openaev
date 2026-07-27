import { type GeneratedReport } from '../../../../../utils/api-types';

export type ReportTemplateFilter = 'ALL' | GeneratedReport['generated_report_template'];
export type TriggerSourceFilter = 'ALL' | GeneratedReport['generated_report_trigger_source'];

export interface GeneratedReportFilters {
  template: ReportTemplateFilter;
  triggerSource: TriggerSourceFilter;
}

export const DEFAULT_GENERATED_REPORT_FILTERS: GeneratedReportFilters = {
  template: 'ALL',
  triggerSource: 'ALL',
};

/**
 * Shared client-side filtering used by the "Access Reports" history lists
 * (per-simulation, global, scenario): by template (Executive/Technical) and
 * by trigger source (Manual/Auto on completion/Scheduled).
 */
export const filterGeneratedReports = (reports: GeneratedReport[], filters: GeneratedReportFilters): GeneratedReport[] => reports.filter((report) => {
  if (filters.template !== 'ALL' && report.generated_report_template !== filters.template) return false;
  if (filters.triggerSource !== 'ALL' && report.generated_report_trigger_source !== filters.triggerSource) return false;
  return true;
});

/** Translation-key label for a trigger source, to be passed through `t()` at render time. */
export const triggerSourceLabel = (source: GeneratedReport['generated_report_trigger_source']): string => {
  switch (source) {
    case 'AUTO_ON_COMPLETION': return 'Auto (on completion)';
    case 'SCHEDULED': return 'Scheduled';
    default: return 'Manual';
  }
};
