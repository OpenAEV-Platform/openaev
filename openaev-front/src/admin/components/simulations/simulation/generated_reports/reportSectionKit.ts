import type { Column, Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { type Translate } from '../../../../../components/i18n';
import {
  type AssetFinding,
  type ControlEffectiveness,
  type DomainScore,
  type ExecutedAction,
  type OutcomeCounts,
  type ReportAnalytics,
  type ReportFinding,
  type TacticScore,
  type TopRisk,
  type TtpScore,
} from './reportAnalytics';
import {
  buildCoverPage,
  buildPageFooter,
  buildPageHeader,
  buildTocPage,
  coverBackground,
  darkHeaderTableLayout,
  gaugeWidgetRow,
  horizontalBarChart,
  lightStatCards,
  lineTrendChart,
  REPORT_COLORS,
  reportSharedStyles,
  scoreColor,
  sectionHeader,
  severityColor,
  type StatCardDef,
  tableHeaderCell,
  type TrendPoint,
} from './reportPdfStyleKit';

/**
 * Section-level pdfmake content builders shared by the redesigned Technical &
 * Executive reports. Each returns ready-to-embed pdfmake `Content` for one of
 * the new spec sections (MITRE tactic heatmap, most undetected/unprevented
 * TTPs graph, performance by security domain, findings, asset findings,
 * security control effectiveness, business impact, top risk, remediation
 * guidelines), so the 6 report builders stay declarative and consistent.
 */

const OUTCOME_COLORS = {
  successful: REPORT_COLORS.danger,
  detected: REPORT_COLORS.warning,
  prevented: REPORT_COLORS.success,
  vulnerability: '#7f1d1d',
};

/** Signed delta text with trend color (green up / red down), ASCII-safe glyphs only. */
const deltaText = (delta: number, higherIsBetter = true): Content => {
  const improved = higherIsBetter ? delta > 0 : delta < 0;
  const worsened = higherIsBetter ? delta < 0 : delta > 0;
  let color = '#666';
  if (improved) color = REPORT_COLORS.success;
  else if (worsened) color = REPORT_COLORS.danger;
  let arrow = '=';
  if (delta > 0) arrow = '^';
  else if (delta < 0) arrow = 'v';
  return {
    text: `${arrow} ${delta > 0 ? '+' : ''}${delta}`,
    color,
    bold: true,
    fontSize: 8,
  };
};

/** Adversarial Exposure Score + prevention/detection/vulnerability gauges. */
export const exposureWidget = (t: Translate, analytics: ReportAnalytics): Content => gaugeWidgetRow([
  {
    label: t('Adversarial exposure'),
    value: `${analytics.exposureScore}%`,
    percentage: 100 - analytics.exposureScore,
    color: scoreColor(100 - analytics.exposureScore),
  },
  {
    label: t('Prevention'),
    value: `${analytics.rates.prevention}%`,
    percentage: analytics.rates.prevention,
  },
  {
    label: t('Detection'),
    value: `${analytics.rates.detection}%`,
    percentage: analytics.rates.detection,
  },
  {
    label: t('Vulnerability'),
    value: `${analytics.rates.vulnerability}%`,
    percentage: 100 - analytics.rates.vulnerability,
    color: scoreColor(100 - analytics.rates.vulnerability),
  },
]);

/** Outcome breakdown cards: successful / detected / prevented / vulnerabilities. */
export const outcomeBreakdown = (t: Translate, counts: OutcomeCounts): Content => ({
  columns: ([
    ['successful', t('Successful (breach)')],
    ['detected', t('Detected')],
    ['prevented', t('Prevented')],
    ['vulnerability', t('Vulnerabilities')],
  ] as const).map(([key, label]) => ({
    width: '*',
    stack: [
      {
        text: String(counts[key]),
        fontSize: 20,
        bold: true,
        color: OUTCOME_COLORS[key],
      },
      {
        text: label.toUpperCase(),
        fontSize: 7,
        bold: true,
        color: '#666',
        characterSpacing: 0.4,
        margin: [0, 2, 0, 0] as [number, number, number, number],
      },
    ],
  })),
  columnGap: 10,
  margin: [0, 6, 0, 16] as [number, number, number, number],
});

/** Performance by security domain (horizontal bar chart). */
export const securityDomainChart = (t: Translate, domains: DomainScore[]): Content => horizontalBarChart(
  domains.map(d => ({
    label: t(d.domain),
    value: d.score,
    color: scoreColor(d.score),
  })),
);

/**
 * MITRE ATT&CK coverage heatmap: the 8 tactics laid out as a colored grid
 * (4 columns x 2 rows), each cell shaded by its coverage score.
 */
export const mitreTacticHeatmap = (t: Translate, tactics: TacticScore[]): Content => {
  const columns = 4;
  const rows: TacticScore[][] = [];
  for (let i = 0; i < tactics.length; i += columns) {
    rows.push(tactics.slice(i, i + columns));
  }
  return {
    table: {
      widths: Array.from({ length: columns }, () => '*'),
      body: rows.map(row => row.map((tactic): TableCell => ({
        stack: [
          {
            text: t(tactic.tactic).toUpperCase(),
            fontSize: 7,
            bold: true,
            color: '#fff',
          },
          {
            text: `${tactic.score}%`,
            fontSize: 16,
            bold: true,
            color: '#fff',
            margin: [0, 4, 0, 0] as [number, number, number, number],
          },
          {
            text: tactic.count > 0 ? `${tactic.count} ${t('techniques')}` : t('no observed technique'),
            fontSize: 6,
            color: '#f0f0f0',
            margin: [0, 2, 0, 0] as [number, number, number, number],
          },
        ],
        fillColor: scoreColor(tactic.score),
        margin: [8, 8, 8, 8] as [number, number, number, number],
      }))),
    },
    layout: {
      hLineWidth: () => 3,
      vLineWidth: () => 3,
      hLineColor: () => '#ffffff',
      vLineColor: () => '#ffffff',
      paddingLeft: () => 0,
      paddingRight: () => 0,
      paddingTop: () => 0,
      paddingBottom: () => 0,
    },
    margin: [0, 6, 0, 16] as [number, number, number, number],
  };
};

/** Most undetected/unprevented TTPs (horizontal bar chart, red-scaled by miss rate). */
export const undetectedTtpsChart = (t: Translate, ttps: TtpScore[]): Content => (ttps.length > 0
  ? horizontalBarChart(ttps.map(ttp => ({
      label: ttp.name,
      value: ttp.missRate,
      color: scoreColor(100 - ttp.missRate),
      displayValue: `${ttp.missRate}% ${t('missed')}`,
    })))
  : {
      text: t('No undetected/unprevented techniques observed - full coverage.'),
      italics: true,
      color: '#666',
      margin: [0, 4, 0, 12] as [number, number, number, number],
    });

/** Findings table: finding / detected endpoint / found date (+ severity). */
export const findingsTable = (t: Translate, findings: ReportFinding[]): Content => (findings.length > 0
  ? {
      table: {
        widths: ['*', 110, 70, 55],
        body: [
          [
            tableHeaderCell(t('Finding')),
            tableHeaderCell(t('Detected endpoint')),
            tableHeaderCell(t('Found date')),
            tableHeaderCell(t('Severity')),
          ],
          ...findings.map((f): TableCell[] => ([
            {
              text: f.finding,
              fontSize: 7,
            },
            {
              text: f.endpoint,
              fontSize: 7,
            },
            {
              text: f.date,
              fontSize: 7,
            },
            {
              text: t(f.severity.toUpperCase()),
              fontSize: 7,
              bold: true,
              color: severityColor(f.severity),
            },
          ])),
        ],
      },
      layout: darkHeaderTableLayout(),
      margin: [0, 6, 0, 16] as [number, number, number, number],
    }
  : {
      text: t('No findings identified.'),
      italics: true,
      color: '#666',
      margin: [0, 6, 0, 16] as [number, number, number, number],
    });

/** Findings count broken down by asset (sorted, worst first). */
export const assetFindingsTable = (t: Translate, assets: AssetFinding[]): Content => (assets.length > 0
  ? {
      table: {
        widths: ['*', 90, 90, 90],
        body: [
          [
            tableHeaderCell(t('Asset')),
            tableHeaderCell(t('Findings')),
            tableHeaderCell(t('Critical / high')),
            tableHeaderCell(t('Worst severity')),
          ],
          ...assets.map((a): TableCell[] => ([
            {
              text: a.asset,
              fontSize: 8,
            },
            {
              text: String(a.count),
              fontSize: 8,
              alignment: 'center',
            },
            {
              text: String(a.critical),
              fontSize: 8,
              alignment: 'center',
              bold: true,
              color: a.critical > 0 ? severityColor('high') : undefined,
            },
            {
              text: t(a.worstSeverity.toUpperCase()),
              fontSize: 8,
              bold: true,
              color: severityColor(a.worstSeverity),
            },
          ])),
        ],
      },
      layout: darkHeaderTableLayout(),
      margin: [0, 6, 0, 16] as [number, number, number, number],
    }
  : {
      text: t('No asset findings identified.'),
      italics: true,
      color: '#666',
      margin: [0, 6, 0, 16] as [number, number, number, number],
    });

/**
 * Security control effectiveness table: EDR / AV / SIEM / XDR / Firewall etc.
 * with score and (optionally) a "vs previous window" comparison delta.
 */
export const securityControlTable = (t: Translate, controls: ControlEffectiveness[], showDelta: boolean): Content => {
  const header: TableCell[] = [
    tableHeaderCell(t('Security control')),
    tableHeaderCell(t('Effectiveness score')),
  ];
  if (showDelta) header.push(tableHeaderCell(t('Comparison')));
  return {
    table: {
      widths: showDelta ? ['*', 120, 100] : ['*', 120],
      body: [
        header,
        ...controls.map((c): TableCell[] => {
          const row: TableCell[] = [
            {
              text: t(c.product),
              fontSize: 8,
            },
            {
              text: `${c.score}%`,
              fontSize: 8,
              bold: true,
              color: scoreColor(c.score),
            },
          ];
          if (showDelta) row.push(deltaText(c.delta) as TableCell);
          return row;
        }),
      ],
    },
    layout: darkHeaderTableLayout(),
    margin: [0, 6, 0, 16] as [number, number, number, number],
  };
};

/**
 * Business impact narrative: fixed 5-bullet list describing what the
 * validated attack scenarios may allow an attacker to do (static copy per the
 * spec's "may allow an attacker to" phrasing).
 */
export const businessImpactBlock = (t: Translate): Content => ({
  stack: [
    {
      text: t('Based on the validated attack scenarios in this assessment, an attacker exploiting the observed gaps may be able to:'),
      margin: [0, 0, 0, 6] as [number, number, number, number],
    },
    {
      ul: [
        t('Access privileged accounts'),
        t('Move laterally across business systems'),
        t('Deploy ransomware'),
        t('Exfiltrate sensitive data'),
        t('Disable security controls'),
      ],
      margin: [0, 0, 0, 12] as [number, number, number, number],
    },
  ],
});

/** Top risk block: the most critical asset, its findings and (optional) comparison. */
export const topRiskBlock = (t: Translate, topRisk: TopRisk | undefined, showDelta: boolean): Content => {
  if (!topRisk) {
    return {
      text: t('No asset carries a significant residual risk in this assessment.'),
      italics: true,
      color: '#666',
      margin: [0, 6, 0, 16] as [number, number, number, number],
    };
  }
  const headerText: Content = {
    text: [
      {
        text: `${t('Most critical asset')}: `,
        bold: true,
      },
      {
        text: topRisk.asset,
        bold: true,
        color: REPORT_COLORS.accent,
      },
      {
        text: `   ${t('Risk score')}: ${topRisk.score}%`,
        color: scoreColor(topRisk.score),
      },
    ],
    margin: [0, 0, 0, showDelta ? 2 : 8] as [number, number, number, number],
  };
  const blocks: Content[] = [headerText];
  if (showDelta) {
    blocks.push({
      columns: [
        {
          width: 'auto',
          text: `${t('vs previous window')}: `,
          fontSize: 8,
          color: '#666',
        },
        {
          width: 'auto',
          ...deltaText(topRisk.delta) as object,
        },
      ],
      columnGap: 4,
      margin: [0, 0, 0, 8] as [number, number, number, number],
    } as Content);
  }
  blocks.push(findingsTable(t, topRisk.findings));
  return { stack: blocks };
};

/** Remediation guidelines: one line per action/finding. */
export const remediationGuidelinesList = (t: Translate, remediations: {
  action: string;
  remediation: string;
}[]): Content => (remediations.length > 0
  ? {
      ul: remediations.map(r => ({
        text: [
          {
            text: `${r.action} - `,
            bold: true,
          },
          { text: r.remediation },
        ],
      })),
      margin: [0, 4, 0, 16] as [number, number, number, number],
    }
  : {
      text: t('No remediation actions required - all executed attacks were prevented.'),
      italics: true,
      color: '#666',
      margin: [0, 6, 0, 16] as [number, number, number, number],
    });

/**
 * Assessment "executed actions" table: every action with its
 * successful/detected/prevented/vulnerability outcome and a one-line
 * remediation (Global/Simulation Technical spec).
 */
export const executedActionsTable = (t: Translate, actions: ExecutedAction[], includeRemediation: boolean): Content => {
  const outcomeLabel: Record<ExecutedAction['outcome'], string> = {
    successful: t('Successful'),
    detected: t('Detected'),
    prevented: t('Prevented'),
    vulnerability: t('Vulnerability'),
  };
  const header: TableCell[] = [
    tableHeaderCell(t('Action')),
    tableHeaderCell(t('Technique')),
    tableHeaderCell(t('Target asset')),
    tableHeaderCell(t('Outcome')),
  ];
  if (includeRemediation) header.push(tableHeaderCell(t('Remediation')));
  return actions.length > 0
    ? {
        table: {
          widths: includeRemediation ? [95, '*', 75, 55, '*'] : [120, '*', 90, 60],
          body: [
            header,
            ...actions.map((a): TableCell[] => {
              const row: TableCell[] = [
                {
                  text: a.name,
                  fontSize: 7,
                },
                {
                  text: a.techniqueName,
                  fontSize: 7,
                },
                {
                  text: a.asset,
                  fontSize: 7,
                },
                {
                  text: outcomeLabel[a.outcome],
                  fontSize: 7,
                  bold: true,
                  color: OUTCOME_COLORS[a.outcome],
                },
              ];
              if (includeRemediation) row.push({
                text: a.remediation,
                fontSize: 6.5,
              });
              return row;
            }),
          ],
        },
        layout: darkHeaderTableLayout(),
        margin: [0, 6, 0, 16] as [number, number, number, number],
      }
    : {
        text: t('No executed action data available.'),
        italics: true,
        color: '#666',
        margin: [0, 6, 0, 16] as [number, number, number, number],
      };
};

/** Simple bullet list of the security agents/collectors used in the assessment. */
export const agentsList = (t: Translate, agents: string[]): Content => (agents.length > 0
  ? {
      ul: agents,
      margin: [0, 4, 0, 16] as [number, number, number, number],
    }
  : {
      text: t('No agent/collector data available.'),
      italics: true,
      color: '#666',
      margin: [0, 6, 0, 16] as [number, number, number, number],
    });

export { deltaText };

/** One section: a heading title plus its already-built pdfmake content blocks. */
export interface SectionDef {
  title: string;
  content: Content[];
}

/**
 * Turns an ordered list of sections into pdfmake content: each section gets an
 * auto-numbered `sectionHeader` (01, 02, ...) whose leaf title node registers a
 * real Table of Contents entry, and every section after the first starts on a
 * new page. Because the numbering is derived from the actual section list, the
 * ToC always reflects exactly the sections present (no stale/hardcoded list).
 */
export const composeSections = (sections: SectionDef[]): Content[] => sections.flatMap((section, index) => [
  sectionHeader(String(index + 1).padStart(2, '0'), section.title, index === 0 ? undefined : { pageBreak: 'before' }),
  ...section.content,
]);

/** A single "Total X: value (vs previous delta)" comparison line. */
export const comparisonLine = (t: Translate, label: string, current: number | string, delta?: number, higherIsBetter = true): Content => ({
  columns: [
    {
      width: 'auto',
      text: `${label}: `,
      bold: true,
    },
    {
      width: 'auto',
      text: String(current),
      bold: true,
      color: REPORT_COLORS.accent,
    },
    ...(delta !== undefined
      ? [{
          width: 'auto',
          text: `  ${t('vs previous')} `,
          fontSize: 8,
          color: '#666',
        }, {
          width: 'auto',
          ...(deltaText(delta, higherIsBetter) as {
            text: string;
            color: string;
          }),
        }]
      : []),
  ],
  columnGap: 4,
  margin: [0, 0, 0, 6] as [number, number, number, number],
});

const gaugeSummaryHeader = (t: Translate): Content => ({
  text: t('Adversarial exposure and posture'),
  style: 'sectionSubtitle',
  margin: [0, 0, 0, 2] as [number, number, number, number],
});

const miniStat = (label: string, value: number | string, color?: string): Column => ({
  width: '*',
  stack: [
    {
      text: String(value),
      fontSize: 18,
      bold: true,
      color: color ?? REPORT_COLORS.accent,
    },
    {
      text: label.toUpperCase(),
      fontSize: 7,
      bold: true,
      color: '#666',
      characterSpacing: 0.4,
      margin: [0, 2, 0, 0] as [number, number, number, number],
    },
  ],
});

/**
 * Technical/Executive summary body: Adversarial Exposure Score + prevention/
 * detection/vulnerability gauges, total adversary & breach count, the
 * outcome breakdown, performance by security domain, and a scope-specific
 * totals line (e.g. "Total scenarios and simulations" or "Total actions").
 */
export const summarySectionContent = (
  t: Translate,
  analytics: ReportAnalytics,
  opts: {
    totalsLine: Content;
    adversaryLabel?: string;
  },
): Content[] => [
  gaugeSummaryHeader(t),
  exposureWidget(t, analytics),
  {
    columns: [
      miniStat(t('Total adversaries'), analytics.totalAdversaries),
      miniStat(t('Total breaches'), analytics.totalBreaches, analytics.totalBreaches > 0 ? REPORT_COLORS.danger : undefined),
      miniStat(t('Total findings'), analytics.findings.length),
    ],
    columnGap: 10,
    margin: [0, 0, 0, 12] as [number, number, number, number],
  },
  {
    text: t('Outcome breakdown'),
    style: 'sectionSubtitle',
  },
  outcomeBreakdown(t, analytics.outcomeCounts),
  {
    text: t('Performance by security domain'),
    style: 'sectionSubtitle',
  },
  securityDomainChart(t, analytics.securityDomains),
  opts.totalsLine,
];

/** MITRE ATT&CK coverage: 8-tactic heatmap + most undetected/unprevented TTPs graph. */
export const mitreCoverageContent = (t: Translate, analytics: ReportAnalytics): Content[] => [
  {
    text: t('Coverage by tactic'),
    style: 'sectionSubtitle',
  },
  mitreTacticHeatmap(t, analytics.tactics),
  {
    text: t('Most undetected / unprevented TTPs'),
    style: 'sectionSubtitle',
  },
  undetectedTtpsChart(t, analytics.undetectedTtps),
];

/** One row of the trend section's simulation list. */
export interface TrendListRow {
  label: string;
  startTime: string;
  score: number;
  delta: number;
}

export interface TrendData {
  points: TrendPoint[];
  rows: TrendListRow[];
  totalFindings: {
    current: number;
    previous: number;
  };
  totalCves: {
    current: number;
    previous: number;
  };
}

/**
 * Builds `TrendData` from a chronological (oldest -> newest) series of
 * per-simulation posture scores: marks the first and last points distinctly,
 * and computes each row's run-to-run delta.
 */
export const buildTrendData = (
  fldt: (input?: string) => string,
  series: {
    label: string;
    startDate?: string;
    score: number;
  }[],
  totals: {
    findings: {
      current: number;
      previous: number;
    };
    cves: {
      current: number;
      previous: number;
    };
  },
): TrendData => ({
  points: series.map((s, i) => ({
    label: s.label,
    value: s.score,
    first: i === 0 && series.length > 1,
    current: i === series.length - 1,
  })),
  rows: series.map((s, i) => ({
    label: s.label,
    startTime: fldt(s.startDate) || 'N/A',
    score: s.score,
    delta: i === 0 ? 0 : s.score - series[i - 1].score,
  })),
  totalFindings: totals.findings,
  totalCves: totals.cves,
});

/**
 * Trend Across Simulations: line chart of the exposure/posture score for each
 * simulation in range (first + last markers distinguished), the full
 * simulation list with start time + score + comparison, and the total
 * findings / total CVEs comparisons. Only used by Global/Scenario reports -
 * Simulation reports have a single run and therefore no trend section.
 */
export const trendSectionContent = (t: Translate, trend: TrendData): Content[] => {
  if (trend.points.length === 0) {
    return [{
      text: t('Not enough simulations in the selected range to build a trend.'),
      italics: true,
      color: '#666',
      margin: [0, 4, 0, 12] as [number, number, number, number],
    }];
  }
  return [
    {
      text: t('Security posture score trend (higher is better; first and last runs highlighted)'),
      style: 'sectionSubtitle',
    },
    lineTrendChart(trend.points),
    {
      text: t('Simulations in range'),
      style: 'sectionSubtitle',
    },
    {
      table: {
        widths: ['*', 120, 70, 70],
        body: [
          [
            tableHeaderCell(t('Simulation')),
            tableHeaderCell(t('Start time')),
            tableHeaderCell(t('Posture score')),
            tableHeaderCell(t('Comparison')),
          ],
          ...trend.rows.map((row): TableCell[] => ([
            {
              text: row.label,
              fontSize: 8,
            },
            {
              text: row.startTime,
              fontSize: 8,
            },
            {
              text: `${row.score}%`,
              fontSize: 8,
              bold: true,
              color: scoreColor(row.score),
            },
            deltaText(row.delta, true) as TableCell,
          ])),
        ],
      },
      layout: darkHeaderTableLayout(),
      margin: [0, 6, 0, 16] as [number, number, number, number],
    },
    comparisonLine(t, t('Total findings'), trend.totalFindings.current, trend.totalFindings.current - trend.totalFindings.previous, false),
    comparisonLine(t, t('Total CVEs'), trend.totalCves.current, trend.totalCves.current - trend.totalCves.previous, false),
    {
      text: '',
      margin: [0, 0, 0, 8] as [number, number, number, number],
    },
  ];
};

export interface ReportDocOptions {
  t: Translate;
  reportKindLabel: string;
  titleMain: string;
  dateLabel: string;
  tlpLabel: string;
  kicker: string;
  subtitle: string;
  statCards: StatCardDef[];
  description: string;
  footerRight: string;
  tocIntro: string;
  sections: SectionDef[];
  defaultFontSize?: number;
}

/**
 * Assembles a complete report `TDocumentDefinitions`: dark cover page,
 * populated Table of Contents, then the ordered sections. Shared by all 6
 * report builders so the pdfmake document shell (page size, header/footer,
 * background, styles, margins) stays identical across every report.
 */
export const assembleReportDoc = (opts: ReportDocOptions): TDocumentDefinitions => ({
  compress: false,
  pageSize: 'A4' as const,
  background: coverBackground,
  header: buildPageHeader(`${opts.reportKindLabel} - ${opts.titleMain}`.toUpperCase(), opts.dateLabel),
  footer: buildPageFooter('FILIGRAN - XTM ONE - OPENAEV'),
  content: [
    ...buildCoverPage({
      t: opts.t,
      tlpLabel: opts.tlpLabel,
      dateLabel: opts.dateLabel,
      kicker: opts.kicker,
      titleMain: opts.titleMain,
      subtitle: opts.subtitle,
      statCards: opts.statCards,
      description: opts.description,
      footerRight: opts.footerRight,
    }),
    ...buildTocPage({
      t: opts.t,
      tocId: 'report-toc',
      intro: opts.tocIntro,
      classificationTitle: opts.t('Classification notice'),
      classificationText: opts.t('This document contains information about your organization\'s security posture. Handle and share according to your internal data classification policy.'),
    }),
    ...composeSections(opts.sections),
  ] as Content,
  styles: {
    ...reportSharedStyles,
    tableTitle: {
      fontSize: 10,
      bold: true,
    },
  },
  defaultStyle: { fontSize: opts.defaultFontSize ?? 9 },
  pageMargins: [40, 60, 40, 50] as [number, number, number, number],
});

export { lightStatCards };
