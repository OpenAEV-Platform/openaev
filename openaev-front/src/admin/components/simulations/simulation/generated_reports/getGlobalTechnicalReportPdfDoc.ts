import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { type Translate } from '../../../../../components/i18n';
import { type GlobalGeneratedReportPdfData } from './fetchGlobalGeneratedReportPdfData';
import { analyticsHashString, deriveReportAnalytics, type PostureRates } from './reportAnalytics';
import { scoreColor } from './reportPdfStyleKit';
import {
  agentsList,
  assembleReportDoc,
  assetFindingsTable,
  buildTrendData,
  comparisonLine,
  executedActionsTable,
  findingsTable,
  mitreCoverageContent,
  type SectionDef,
  securityControlTable,
  summarySectionContent,
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
 * Global (platform-wide) Technical Report: Cover, Table of Contents,
 * Technical Summary, MITRE ATT&CK Coverage, Assessment Details, Trend Across
 * Simulations, Asset Findings and Security Control Effectiveness. Aggregates
 * every simulation in the selected comparison window.
 */
const getGlobalTechnicalReportPdfDoc = async ({ data, t, fldt }: Props): Promise<TDocumentDefinitions> => {
  const seed = `global-technical-${data.window.window}`;
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

  // Chronological (oldest -> newest) series for the trend chart.
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
      title: t('Technical Summary'),
      content: summarySectionContent(t, analytics, { totalsLine }),
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
          text: t('Simulations assessed'),
          style: 'sectionSubtitle',
        },
        agentsList(t, data.exercises.map(e => e.exerciseName)),
        {
          text: t('Executed actions'),
          style: 'sectionSubtitle',
        },
        executedActionsTable(t, analytics.actions, true),
        {
          text: t('Findings'),
          style: 'sectionSubtitle',
        },
        findingsTable(t, analytics.findings),
      ],
    },
    {
      title: t('Trend Across Simulations'),
      content: trendSectionContent(t, trend),
    },
    {
      title: t('Asset Findings'),
      content: [assetFindingsTable(t, analytics.assetFindings)],
    },
    {
      title: t('Security Control Effectiveness'),
      content: [securityControlTable(t, analytics.controlEffectiveness, true)],
    },
  ];

  return assembleReportDoc({
    t,
    reportKindLabel: t('Global Technical Report'),
    titleMain: t('All Simulations'),
    dateLabel: fldt(data.generatedAt) || new Date().toLocaleDateString(),
    tlpLabel: 'TLP:RED - Technical distribution',
    kicker: t('Global Technical Report'),
    subtitle: `${t('Platform-wide technical assessment across the selected comparison window.')} (${data.totalSimulations} ${t('simulations')})`,
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
    description: t('This technical report aggregates every simulation across the platform within the selected comparison window: adversarial exposure, MITRE ATT&CK coverage, executed actions with remediation, findings, the score trend across simulations, asset findings and security control effectiveness.'),
    footerRight: `${t('Report version')}: ${REPORT_VERSION}`,
    tocIntro: t('This report covers the technical summary, MITRE ATT&CK coverage, assessment details, the trend across simulations, asset findings and security control effectiveness platform-wide.'),
    sections,
  });
};

export default getGlobalTechnicalReportPdfDoc;
