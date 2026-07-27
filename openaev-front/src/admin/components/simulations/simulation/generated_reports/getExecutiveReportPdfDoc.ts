import type { TDocumentDefinitions } from 'pdfmake/interfaces';

import { type Translate } from '../../../../../components/i18n';
import { type GeneratedReportPdfData } from './fetchGeneratedReportPdfData';
import { deriveReportAnalytics, postureRatesFromExpectations } from './reportAnalytics';
import { scoreColor } from './reportPdfStyleKit';
import {
  assembleReportDoc,
  businessImpactBlock,
  comparisonLine,
  mitreCoverageContent,
  type SectionDef,
  securityControlTable,
  summarySectionContent,
  topRiskBlock,
} from './reportSectionKit';
import { buildItemizedAttackRows } from './technicalVariantAdapters';

const REPORT_VERSION = 'v6.0';

interface Props {
  data: GeneratedReportPdfData;
  t: Translate;
  fldt: (input?: string) => string;
}

/**
 * Simulation Executive Report.
 *
 * Single simulation run for management: Cover, Table of Contents, Executive
 * Summary, MITRE ATT&CK Coverage, Business Impact, Top Risk and Security
 * Control Effectiveness. As with the Simulation Technical report, a single
 * run has no prior run to compare against, so there is NO "Trend Across
 * Simulations" section and NO "vs previous" comparison deltas - only this
 * run's absolute numbers are shown. (The user's pasted spec listed a
 * comparison-flavored summary line here, but that contradicts the
 * established "Simulation reports have no comparison" rule; we keep the
 * summary metrics but render absolute values only.)
 */
const getExecutiveReportPdfDoc = async ({ data, t, fldt }: Props): Promise<TDocumentDefinitions> => {
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
      title: t('Executive Summary'),
      content: summarySectionContent(t, analytics, { totalsLine: comparisonLine(t, t('Total actions executed'), analytics.actions.length) }),
    },
    {
      title: t('MITRE ATT&CK Coverage'),
      content: mitreCoverageContent(t, analytics),
    },
    {
      title: t('Business Impact'),
      content: [businessImpactBlock(t)],
    },
    {
      // Single run: no comparison possible, absolute figures only.
      title: t('Top Risk'),
      content: [topRiskBlock(t, analytics.topRisk, false)],
    },
    {
      title: t('Security Control Effectiveness'),
      content: [securityControlTable(t, analytics.controlEffectiveness, false)],
    },
  ];

  return assembleReportDoc({
    t,
    reportKindLabel: t('Simulation Executive Report'),
    titleMain: exercise.exercise_name,
    dateLabel: fldt(exercise.exercise_start_date) || new Date().toLocaleDateString(),
    tlpLabel: 'TLP:AMBER - Restricted distribution',
    kicker: t('Simulation Executive Report'),
    subtitle: t('Management-level summary of a single simulation run: exposure, posture, business impact and top risk.'),
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
    description: t('This executive report summarizes a single simulation run for management and non-technical stakeholders: adversarial exposure, MITRE ATT&CK coverage, the business impact of validated attack scenarios, the top risk asset and security control effectiveness.'),
    footerRight: `${t('Report version')}: ${REPORT_VERSION} - ${t('Simulation ID')}: ${exercise.exercise_id}`,
    tocIntro: t('This report covers the executive summary, MITRE ATT&CK coverage, business impact, top risk and security control effectiveness for this simulation.'),
    sections,
  });
};

export default getExecutiveReportPdfDoc;
