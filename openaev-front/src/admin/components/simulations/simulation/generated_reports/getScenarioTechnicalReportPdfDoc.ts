import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { type Translate } from '../../../../../components/i18n';
import { type ScenarioGeneratedReportPdfData } from './fetchScenarioGeneratedReportPdfData';
import { analyticsHashString, deriveReportAnalytics, type PostureRates, postureRatesFromExpectations } from './reportAnalytics';
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
  remediationGuidelinesList,
  type SectionDef,
  securityControlTable,
  summarySectionContent,
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
 * Scenario Technical Report: Cover, Table of Contents, Technical Summary,
 * MITRE ATT&CK Coverage, Assessment Details, Trend Across Simulations, Asset
 * Findings, Remediation Guidelines and Security Control Effectiveness. Scoped
 * to every run of this scenario within the selected comparison window. Unlike
 * the Global Technical report, the per-action one-line remediation lives in a
 * dedicated "Remediation Guidelines" section rather than inline in Assessment
 * Details (per the scenario spec).
 */
const getScenarioTechnicalReportPdfDoc = async ({ data, t, fldt }: Props): Promise<TDocumentDefinitions> => {
  const seed = `scenario-technical-${data.scenario.scenario_id}-${data.window.window}`;
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
          text: t('Scenarios assessed'),
          style: 'sectionSubtitle',
        },
        agentsList(t, [data.scenario.scenario_name]),
        {
          text: t('Executed actions'),
          style: 'sectionSubtitle',
        },
        // Scenario spec: no inline remediation here; it lives in Remediation Guidelines below.
        executedActionsTable(t, analytics.actions, false),
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
      title: t('Remediation Guidelines'),
      content: [remediationGuidelinesList(t, analytics.remediations)],
    },
    {
      title: t('Security Control Effectiveness'),
      content: [securityControlTable(t, analytics.controlEffectiveness, true)],
    },
  ];

  return assembleReportDoc({
    t,
    reportKindLabel: t('Scenario Technical Report'),
    titleMain: data.scenario.scenario_name,
    dateLabel: fldt(data.generatedAt) || new Date().toLocaleDateString(),
    tlpLabel: 'TLP:RED - Technical distribution',
    kicker: t('Scenario Technical Report'),
    subtitle: `${data.scenario.scenario_subtitle ?? t('Scenario technical assessment across the selected comparison window.')} (${data.runs.length} ${t('simulations')})`,
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
    description: t('This technical report aggregates every run of this scenario within the selected comparison window: adversarial exposure, MITRE ATT&CK coverage, executed actions, findings, the score trend across simulations, asset findings, remediation guidelines and security control effectiveness.'),
    footerRight: `${t('Report version')}: ${REPORT_VERSION} - ${t('Scenario ID')}: ${data.scenario.scenario_id}`,
    tocIntro: t('This report covers the technical summary, MITRE ATT&CK coverage, assessment details, the trend across simulations, asset findings, remediation guidelines and security control effectiveness for this scenario.'),
    sections,
  });
};

export default getScenarioTechnicalReportPdfDoc;
