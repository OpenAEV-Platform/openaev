import type { TDocumentDefinitions } from 'pdfmake/interfaces';

import { type Translate } from '../../../../../components/i18n';
import { type GeneratedReportPdfData } from './fetchGeneratedReportPdfData';
import { deriveReportAnalytics, postureRatesFromExpectations } from './reportAnalytics';
import { scoreColor } from './reportPdfStyleKit';
import {
  agentsList,
  assembleReportDoc,
  assetFindingsTable,
  comparisonLine,
  executedActionsTable,
  findingsTable,
  mitreCoverageContent,
  remediationGuidelinesList,
  type SectionDef,
  securityControlTable,
  summarySectionContent,
} from './reportSectionKit';
import { buildItemizedAttackRows } from './technicalVariantAdapters';

/** Report content version, shown on every cover page. */
const REPORT_VERSION = 'v6.0';

interface Props {
  data: GeneratedReportPdfData;
  t: Translate;
  fldt: (input?: string) => string;
}

/**
 * Simulation Technical Report.
 *
 * Single simulation run: Cover, Table of Contents, Technical Summary, MITRE
 * ATT&CK Coverage, Assessment Details, Asset Findings, Remediation
 * Guidelines and Security Control Effectiveness. A single run has no prior
 * run to compare against, so there is intentionally NO "Trend Across
 * Simulations" section and NO "vs previous" comparison deltas anywhere -
 * only this run's absolute numbers are shown (consistent with the rest of
 * this codebase's "Simulation reports have no comparison" convention).
 */
const getTechnicalReportPdfDoc = async ({ data, t, fldt }: Props): Promise<TDocumentDefinitions> => {
  const { exercise, expectationResults, injects, attackPatternResults } = data;

  const rates = postureRatesFromExpectations(expectationResults);
  const rows = await buildItemizedAttackRows(injects, attackPatternResults, t);
  const analytics = deriveReportAnalytics({
    seed: exercise.exercise_id,
    rows,
    rates,
    t,
    fldt,
  });

  const sections: SectionDef[] = [
    {
      title: t('Technical Summary'),
      content: summarySectionContent(t, analytics, {
        // Single run: totals reflect the actions that ran, not a scenario/simulation count.
        totalsLine: comparisonLine(t, t('Total actions executed'), analytics.actions.length),
      }),
    },
    {
      title: t('MITRE ATT&CK Coverage'),
      content: mitreCoverageContent(t, analytics),
    },
    {
      title: t('Assessment Details'),
      content: [
        {
          text: t('Agents and collectors used'),
          style: 'sectionSubtitle',
        },
        agentsList(t, analytics.agents),
        {
          text: t('Executed actions'),
          style: 'sectionSubtitle',
        },
        executedActionsTable(t, analytics.actions, false),
        {
          text: t('Findings'),
          style: 'sectionSubtitle',
        },
        findingsTable(t, analytics.findings),
      ],
    },
    {
      title: t('Asset Findings'),
      content: [assetFindingsTable(t, analytics.assetFindings)],
    },
    {
      title: t('Remediation Guidelines'),
      content: [remediationGuidelinesList(t, analytics.remediations)],
    },
    {
      // Single run: no cross-simulation comparison possible, so show this run's per-product score only.
      title: t('Security Control Effectiveness'),
      content: [securityControlTable(t, analytics.controlEffectiveness, false)],
    },
  ];

  return assembleReportDoc({
    t,
    reportKindLabel: t('Simulation Technical Report'),
    titleMain: exercise.exercise_name,
    dateLabel: fldt(exercise.exercise_start_date) || new Date().toLocaleDateString(),
    tlpLabel: 'TLP:RED - Technical distribution',
    kicker: t('Simulation Technical Report'),
    subtitle: t('In-depth technical assessment of a single simulation run: exposure, MITRE coverage, findings and remediation.'),
    statCards: [
      {
        label: t('Success rate'),
        value: `${rates.success}%`,
        color: scoreColor(rates.success),
      },
      {
        label: t('Detection'),
        value: `${rates.detection}%`,
        color: scoreColor(rates.detection),
      },
      {
        label: t('Prevention'),
        value: `${rates.prevention}%`,
        color: scoreColor(rates.prevention),
      },
      {
        label: t('Vulnerability'),
        value: `${rates.vulnerability}%`,
        color: scoreColor(100 - rates.vulnerability),
      },
    ],
    description: t('This technical report documents a single simulation run for security engineers and SOC analysts: adversarial exposure, MITRE ATT&CK coverage, executed actions with detection/prevention outcomes, findings, asset findings, remediation guidelines and security control effectiveness.'),
    footerRight: `${t('Report version')}: ${REPORT_VERSION} - ${t('Simulation ID')}: ${exercise.exercise_id}`,
    tocIntro: t('This report covers the technical summary, MITRE ATT&CK coverage, assessment details, asset findings, remediation guidelines and security control effectiveness for this simulation.'),
    sections,
  });
};

export default getTechnicalReportPdfDoc;
