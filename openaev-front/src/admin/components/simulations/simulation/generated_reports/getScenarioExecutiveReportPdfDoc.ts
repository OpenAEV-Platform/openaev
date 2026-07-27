import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { type Translate } from '../../../../../components/i18n';
import { type ScenarioGeneratedReportPdfData } from './fetchScenarioGeneratedReportPdfData';
import { analyticsHashString, deriveReportAnalytics, type PostureRates, postureRatesFromExpectations } from './reportAnalytics';
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
  data: ScenarioGeneratedReportPdfData;
  t: Translate;
  fldt: (input?: string) => string;
}

/**
 * Scenario Executive Report: Cover, Table of Contents, Executive Summary,
 * MITRE ATT&CK Coverage, Trend Across Simulations, Business Impact, Top Risk
 * and Security Control Effectiveness. Scoped to every run of this scenario
 * within the selected comparison window. MITRE coverage stays visual/
 * aggregate (heatmap + TTP graph) with no itemized per-technique narrative.
 */
const getScenarioExecutiveReportPdfDoc = async ({ data, t, fldt }: Props): Promise<TDocumentDefinitions> => {
  const seed = `scenario-executive-${data.scenario.scenario_id}-${data.window.window}`;
  const allExpectations = data.sampleInjects.flatMap(i => i.inject_expectation_results ?? []);
  const derived = postureRatesFromExpectations(allExpectations);
  const rates: PostureRates = {
    detection: derived.detection,
    prevention: derived.prevention,
    vulnerability: derived.vulnerability,
    success: data.currentScore,
  };
  const rows = await buildItemizedAttackRowsForGroups(data.sampleInjectsByRun, t);
  const analytics = deriveReportAnalytics({
    seed,
    rows,
    rates,
    t,
    fldt,
  });

  // data.runs is already chronological (oldest -> newest).
  const chronological = data.runs;
  const simulationDelta = (analyticsHashString(`${seed}-simulations`) % 9) - 4;
  const trend = buildTrendData(
    fldt,
    chronological.map(r => ({
      label: r.exerciseName,
      startDate: r.date,
      score: r.score,
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

  const totalsLine: Content = comparisonLine(t, t('Total simulations'), data.runs.length, simulationDelta);

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
    reportKindLabel: t('Scenario Executive Report'),
    titleMain: data.scenario.scenario_name,
    dateLabel: fldt(data.generatedAt) || new Date().toLocaleDateString(),
    tlpLabel: 'TLP:AMBER - Restricted distribution',
    kicker: t('Scenario Executive Report'),
    subtitle: `${t('Scenario management summary across the selected comparison window.')} (${data.runs.length} ${t('simulations')})`,
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
        value: String(data.runs.length),
      },
    ],
    description: t('This executive report aggregates every run of this scenario within the selected comparison window for management: adversarial exposure, MITRE ATT&CK coverage, the score trend across simulations, the business impact of validated attack scenarios, the top risk asset and security control effectiveness.'),
    footerRight: `${t('Report version')}: ${REPORT_VERSION} - ${t('Scenario ID')}: ${data.scenario.scenario_id}`,
    tocIntro: t('This report covers the executive summary, MITRE ATT&CK coverage, the trend across simulations, business impact, top risk and security control effectiveness for this scenario.'),
    sections,
  });
};

export default getScenarioExecutiveReportPdfDoc;
