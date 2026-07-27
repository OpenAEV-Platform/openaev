import { type GeneratedReportInput } from '../../../../../utils/api-types';

/**
 * The 2 fixed, hard-coded report templates.
 * Intentionally NOT user editable/creatable: no clone, no preview, no section
 * toggling, no variant selection. Only the built-in list below is exposed in
 * the UI. A future "Remediation" template is intentionally NOT included yet.
 */

export type ReportTemplateKey = GeneratedReportInput['generated_report_template'];

export interface ReportTemplateDefinition {
  key: ReportTemplateKey;
  label: string;
  description: string;
}

export const REPORT_TEMPLATES: ReportTemplateDefinition[] = [
  {
    key: 'EXECUTIVE',
    label: 'Executive Report',
    description: 'High level summary for management: adversarial exposure score, prevention/detection/vulnerability posture, ATT&CK coverage heatmap, business impact and top risk.',
  },
  {
    key: 'TECHNICAL',
    label: 'Technical Report',
    description: 'In-depth analysis for security teams: ATT&CK coverage heatmap, assessment details, findings, asset findings, remediation guidelines and security control effectiveness.',
  },
];
