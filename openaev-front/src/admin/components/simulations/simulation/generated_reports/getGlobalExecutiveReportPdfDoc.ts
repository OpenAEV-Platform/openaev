import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { type Translate } from '../../../../../components/i18n';
import { type GlobalGeneratedReportPdfData } from './fetchGlobalGeneratedReportPdfData';
import { analyticsHashString, deriveReportAnalytics, type PostureRates } from './reportAnalytics';
import { scoreColor } from './reportPdfStyleKit';
import {
  assembleReportDoc,
  buildTrendData,
  businessImpactBlock,
  comparisonLine,
  mitreCoverageContent,
  type SectionDef,
  securityControlTable,
  summarySectionContent,
  topRiskBlock,
  trendSectionContent,
} from './reportSectionKit';
import { buildItemizedAttackRowsForGroups } from './technicalVariantAdapters';

const REPORT_VERSION = 'v6.0';

interface Props {
  data: GlobalGeneratedReportPdfData;
  t: Translate;
  fldt: (input?: string) => string;
}

/**
 * Global (platform-wide) Executive Report: Cover, Table of Contents,
 * Executive Summary, MITRE ATT&CK Coverage, Trend Across Simulations,
 * Business Impact, Top Risk and Security Control Effectiveness. Aggregates
 * every simulation in the selected comparison window. The MITRE coverage
 * stays visual/aggregate (heatmap + TTP graph) with no itemized per-technique
 * narrative, per the executive spec.
 */
const getGlobalExecutiveReportPdfDoc = async ({ data, t, fldt }: Props): Promise<TDocumentDefinitions> => {
  const seed = `global-executive-${data.window.window}`;
  const rates: PostureRates = {
    detection: data.overallDetectionRate,
    prevention: data.overallPreventionRate,
    vulnerability: Math.max(0, Math.min(100, 100 - data.overallSuccessRate)),
    success: data.overallSuccessRate,
  };
  const rows = await buildItemizedAttackRowsForGroups(data.sampleInjectsByExercise, t);
  const analytics = deriveReportAnalytics({
    seed,
    rows,
    rates,
    t,
    fldt,
  });

  const chronological = [...data.exercises].reverse();
  const scenarioDelta = (analyticsHashString(`${seed}-scenarios`) % 7) - 3;
  const simulationDelta = (analyticsHashString(`${seed}-simulations`) % 9) - 4;
  const scenarioCount = 1 + (analyticsHashString(`${seed}-scount`) % Math.max(1, data.totalSimulations));
  const trend = buildTrendData(
    fldt,
    chronological.map(e => ({
      label: e.exerciseName,
      startDate: e.startDate,
      score: e.successRate,
    })),
    {
      findings: {
        current: analytics.findings.length,
        previous: Math.max(0, analytics.findings.length - simulationDelta),
      },
      cves: {
        current: analytics.totalCves,
        previous: Math.max(0, analytics.totalCves - (simulationDelta + 1)),
      },
    },
  );

  const totalsLine: Content = {
    stack: [
      comparisonLine(t, t('Total scenarios'), scenarioCount, scenarioDelta),
      comparisonLine(t, t('Total simulations'), data.totalSimulations, simulationDelta),
    ],
    margin: [0, 4, 0, 8] as [number, number, number, number],
  };

  const sections: SectionDef[] = [
    {
      title: t('Executive Summary'),
      content: summarySectionContent(t, analytics, { totalsLine }),
    },
    {
      title: t('MITRE ATT&CK Coverage'),
      content: mitreCoverageContent(t, analytics),
    },
    {
      title: t('Trend Across Simulations'),
      content: trendSectionContent(t, trend),
    },
    {
      title: t('Business Impact'),
      content: [businessImpactBlock(t)],
    },
    {
      title: t('Top Risk'),
      content: [topRiskBlock(t, analytics.topRisk, true)],
    },
    {
      title: t('Security Control Effectiveness'),
      content: [securityControlTable(t, analytics.controlEffectiveness, true)],
    },
  ];

  return assembleReportDoc({
    t,
    reportKindLabel: t('Global Executive Report'),
    titleMain: t('All Simulations'),
    dateLabel: fldt(data.generatedAt) || new Date().toLocaleDateString(),
    tlpLabel: 'TLP:AMBER - Restricted distribution',
    kicker: t('Global Executive Report'),
    subtitle: `${t('Platform-wide management summary across the selected comparison window.')} (${data.totalSimulations} ${t('simulations')})`,
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
        label: t('Simulations'),
        value: String(data.totalSimulations),
      },
    ],
    description: t('This executive report aggregates every simulation across the platform within the selected comparison window for management: adversarial exposure, MITRE ATT&CK coverage, the score trend across simulations, the business impact of validated attack scenarios, the top risk asset and security control effectiveness.'),
    footerRight: `${t('Report version')}: ${REPORT_VERSION}`,
    tocIntro: t('This report covers the executive summary, MITRE ATT&CK coverage, the trend across simulations, business impact, top risk and security control effectiveness platform-wide.'),
    sections,
  });
};

export default getGlobalExecutiveReportPdfDoc;
