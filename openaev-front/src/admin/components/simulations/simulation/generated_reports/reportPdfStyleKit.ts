import type { Content, ContentCanvas, ContentText, Style, TableCell } from 'pdfmake/interfaces';

import { type Translate } from '../../../../../components/i18n';

/**
 * Shared visual language for the "Generated Reports" PDFs (Executive &
 * Technical templates), modeled after the reference design provided by the
 * product owner (dark cover page + TLP banner, stat cards, numbered
 * sections with a red accent rule, dark-header tables, callout boxes,
 * auto-generated table of contents). Rendered entirely with pdfmake - the
 * same library already used by the existing dashboard PDF export - so no
 * new PDF/design library is introduced.
 */
export const REPORT_COLORS = {
  navy: '#0b1826',
  navyLight: '#132538',
  card: '#152a40',
  accent: '#e0442e',
  border: '#26405c',
  textOnDarkPrimary: '#ffffff',
  textOnDarkMuted: '#93a3b8',
  headerDark: '#0f2137',
  rowAlt: '#f4f6f8',
  tableBorder: '#d7dce2',
  success: '#2e7d32',
  warning: '#ed6c02',
  danger: '#c62828',
};

export const scoreColor = (percentage: number): string => {
  if (percentage >= 75) return REPORT_COLORS.success;
  if (percentage >= 50) return REPORT_COLORS.warning;
  return REPORT_COLORS.danger;
};

export const severityColor = (severity?: string): string => {
  switch ((severity ?? '').toLowerCase()) {
    case 'critical': return '#7f1d1d';
    case 'high': return REPORT_COLORS.danger;
    case 'medium': return REPORT_COLORS.warning;
    default: return REPORT_COLORS.success;
  }
};

/**
 * Same status colors as the product's `colorStyles`/`getStatusColor`
 * (`src/components/Color.ts`, `src/utils/statusUtils.ts`), so a "Prevented"
 * / "Detected" badge in a report looks exactly like it does in the app.
 */
export const EXPECTATION_STATUS_COLORS: Record<string, string> = {
  SUCCESS: '#4caf50',
  FAILED: '#f44336',
  PARTIAL: '#ff9800',
  PENDING: '#607d8b',
};

export const expectationStatusColor = (status?: string): string => (
  EXPECTATION_STATUS_COLORS[(status ?? '').toUpperCase()] ?? EXPECTATION_STATUS_COLORS.PENDING
);

/** Mirrors `computeInjectExpectationLabel` (src/utils/statusUtils.ts) so labels match the app exactly. */
const EXPECTATION_STATUS_LABELS: Record<string, Record<string, string>> = {
  SUCCESS: {
    PREVENTION: 'Prevented',
    DETECTION: 'Detected',
    VULNERABILITY: 'Not vulnerable',
  },
  FAILED: {
    PREVENTION: 'Not Prevented',
    DETECTION: 'Not Detected',
    VULNERABILITY: 'Vulnerable',
  },
  PARTIAL: {
    PREVENTION: 'Partially Prevented',
    DETECTION: 'Partially Detected',
    VULNERABILITY: 'Partially Vulnerable',
  },
  PENDING: {
    PREVENTION: 'Pending',
    DETECTION: 'Pending',
    VULNERABILITY: 'Pending',
  },
};

export const expectationStatusLabel = (status?: string, type?: string): string => (
  EXPECTATION_STATUS_LABELS[(status ?? '').toUpperCase()]?.[(type ?? '').toUpperCase()] ?? (status ?? 'N/A')
);

/**
 * SVG path data taken directly from the product's own icon set
 * (`@mui/icons-material` `ShieldOutlined` / `TrackChanges` / `BugReport`,
 * used by `src/admin/components/common/ExpectationIconByType.tsx`) so
 * detection/prevention/vulnerability icons in the PDF match the app.
 */
const EXPECTATION_ICON_PATHS: Record<string, string> = {
  PREVENTION: 'M12 2 4 5v6.09c0 5.05 3.41 9.76 8 10.91 4.59-1.15 8-5.86 8-10.91V5zm6 9.09c0 4-2.55 7.7-6 8.83-3.45-1.13-6-4.82-6-8.83v-4.7l6-2.25 6 2.25z',
  DETECTION: 'm19.07 4.93-1.41 1.41C19.1 7.79 20 9.79 20 12c0 4.42-3.58 8-8 8s-8-3.58-8-8c0-4.08 3.05-7.44 7-7.93v2.02C8.16 6.57 6 9.03 6 12c0 3.31 2.69 6 6 6s6-2.69 6-6c0-1.66-.67-3.16-1.76-4.24l-1.41 1.41C15.55 9.9 16 10.9 16 12c0 2.21-1.79 4-4 4s-4-1.79-4-4c0-1.86 1.28-3.41 3-3.86v2.14c-.6.35-1 .98-1 1.72 0 1.1.9 2 2 2s2-.9 2-2c0-.74-.4-1.38-1-1.72V2h-1C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10c0-2.76-1.12-5.26-2.93-7.07',
  VULNERABILITY: 'M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 2.17C12.96 5.06 12.49 5 12 5s-.96.06-1.41.17L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20zm-6 8h-4v-2h4zm0-4h-4v-2h4z',
};

/** Small colored icon (matching the app's expectation-type icon) as inline SVG content. */
export const expectationIconSvg = (type: string, color: string, size = 12): {
  svg?: string;
  text?: string;
  width: number;
  height?: number;
} => {
  const path = EXPECTATION_ICON_PATHS[(type ?? '').toUpperCase()];
  if (!path) return {
    text: '',
    width: size,
  };
  return {
    svg: `<svg width="${size}" height="${size}" viewBox="0 0 24 24"><path d="${path}" fill="${color}"/></svg>`,
    width: size,
    height: size,
  };
};

/**
 * Icon + colored badge for a detection/prevention/vulnerability result,
 * reusing the exact icon and color the app uses for the same status
 * (`ExpectationIconByType.tsx`, `statusUtils.ts`). Used in both templates
 * everywhere a Detected/Not Detected/Prevented/Not Prevented result is shown.
 */
export const expectationStatusBadge = (type: string, status?: string): Content => {
  const color = expectationStatusColor(status);
  const label = expectationStatusLabel(status, type);
  const iconSize = 10;
  return {
    columns: [
      { ...expectationIconSvg(type, color, iconSize) } as unknown as Content,
      {
        width: 'auto',
        text: label,
        color,
        bold: true,
        fontSize: 8,
        margin: [3, 0, 0, 0] as [number, number, number, number],
      },
    ],
    columnGap: 2,
  };
};

/** Dark-header table layout used for every data table across both templates. */
export const darkHeaderTableLayout = () => ({
  hLineWidth: (i: number) => (i === 0 || i === 1 ? 1 : 0.5),
  vLineWidth: () => 0,
  hLineColor: () => REPORT_COLORS.tableBorder,
  paddingLeft: () => 8,
  paddingRight: () => 8,
  paddingTop: () => 6,
  paddingBottom: () => 6,
  fillColor: (rowIndex: number) => {
    if (rowIndex === 0) return REPORT_COLORS.headerDark;
    return rowIndex % 2 === 0 ? REPORT_COLORS.rowAlt : null;
  },
});

export const tableHeaderCell = (title: string): TableCell => ({
  text: title,
  style: 'tableHeaderCell',
});

/** Small colored pill/badge, e.g. severity or status labels. */
export const badge = (text: string, bgColor: string, textColor = '#ffffff'): Content => ({
  table: {
    widths: ['auto'],
    body: [[{
      text,
      color: textColor,
      fontSize: 7,
      bold: true,
      fillColor: bgColor,
      margin: [4, 2, 4, 2] as [number, number, number, number],
    }]],
  },
  layout: 'noBorders',
});

/** Light callout box with a colored left rule (classification notice / intelligence anchor style). */
export const calloutBox = (title: string, body: Content | Content[], borderColor = REPORT_COLORS.accent): Content => ({
  table: {
    widths: ['*'],
    body: [[{
      stack: [
        {
          text: title,
          bold: true,
          fontSize: 9,
          margin: [0, 0, 0, 4] as [number, number, number, number],
        },
        ...(Array.isArray(body) ? body : [body]),
      ],
      fillColor: '#f4f6f8',
    }]],
  },
  layout: {
    hLineWidth: () => 0,
    vLineWidth: (i: number) => (i === 0 ? 3 : 0),
    vLineColor: () => borderColor,
    paddingLeft: () => 12,
    paddingRight: () => 10,
    paddingTop: () => 10,
    paddingBottom: () => 10,
  },
  margin: [0, 8, 0, 8] as [number, number, number, number],
});

export interface StatCardDef {
  label: string;
  value: string;
  color?: string;
}

/**
 * Donut/ring gauge widget (SVG `stroke-dasharray` trick), e.g. for an
 * overall security posture score. A real graphical widget rather than a
 * table, reusing the same percentage-based color coding (`scoreColor`) as
 * the rest of the report.
 */
export const donutGaugeSvg = (percentage: number, color: string, size = 90, strokeWidth = 12): {
  svg: string;
  width: number;
  height: number;
} => {
  const clamped = Math.max(0, Math.min(100, percentage));
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const dash = (clamped / 100) * circumference;
  const center = size / 2;
  return {
    svg: `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
      <circle cx="${center}" cy="${center}" r="${radius}" fill="none" stroke="#e4e7eb" stroke-width="${strokeWidth}" />
      <circle cx="${center}" cy="${center}" r="${radius}" fill="none" stroke="${color}" stroke-width="${strokeWidth}"
        stroke-dasharray="${dash.toFixed(2)} ${(circumference - dash).toFixed(2)}" stroke-linecap="round"
        transform="rotate(-90 ${center} ${center})" />
    </svg>`,
    width: size,
    height: size,
  };
};

/** Donut gauge + label/value stacked next to it - a self-contained "widget" block. */
export const gaugeWidget = (label: string, value: string, percentage: number, color?: string, size = 84): Record<string, unknown> => ({
  columns: [
    { ...donutGaugeSvg(percentage, color ?? scoreColor(percentage), size) },
    {
      width: '*',
      stack: [
        {
          text: label.toUpperCase(),
          fontSize: 7,
          bold: true,
          color: '#666',
          characterSpacing: 0.4,
        },
        {
          text: value,
          fontSize: 20,
          bold: true,
          color: color ?? scoreColor(percentage),
          margin: [0, 4, 0, 0] as [number, number, number, number],
        },
      ],
      margin: [12, size / 2 - 18, 0, 0] as [number, number, number, number],
    },
  ],
  columnGap: 4,
});

/** Row of gauge widgets, e.g. Overall / Detection / Prevention score gauges side by side. */
export const gaugeWidgetRow = (gauges: {
  label: string;
  value: string;
  percentage: number;
  color?: string;
}[]): Content => ({
  columns: gauges.map(g => ({
    width: '*',
    ...gaugeWidget(g.label, g.value, g.percentage, g.color, 72),
  })),
  columnGap: 16,
  margin: [0, 8, 0, 20] as [number, number, number, number],
} as unknown as Content);

/**
 * Horizontal bar-chart widget (simple graph, no new chart library): one
 * labeled colored bar per item, proportional to `maxValue`.
 */
export const horizontalBarChart = (items: {
  label: string;
  value: number;
  color: string;
  displayValue?: string;
}[], maxValue = 100): Content => ({
  stack: items.map(item => ({
    stack: [
      {
        columns: [
          {
            width: '*',
            text: item.label,
            fontSize: 9,
            bold: true,
          },
          {
            width: 'auto',
            text: item.displayValue ?? `${item.value}%`,
            fontSize: 9,
            bold: true,
            color: item.color,
            alignment: 'right',
          },
        ],
      },
      {
        table: {
          widths: [`${Math.max((item.value / maxValue) * 100, 1)}%`, `${100 - Math.max((item.value / maxValue) * 100, 1)}%`],
          body: [[
            {
              text: '',
              fillColor: item.color,
            },
            {
              text: '',
              fillColor: '#eee',
            },
          ]],
        },
        layout: 'noBorders',
        margin: [0, 2, 0, 10] as [number, number, number, number],
      },
    ],
  })),
});

export interface TrendPoint {
  label: string;
  value: number;
  /** The most recent / latest point - rendered larger and tagged "Latest". */
  current?: boolean;
  /** The first / earliest point in the range - rendered as a hollow diamond tagged "First". */
  first?: boolean;
}

/**
 * Graphical score-trend line chart (SVG polyline + point markers + gridlines),
 * used by every Executive report (Simulation / Scenario / Global) to
 * visualize the score across runs within the selected comparison window as
 * an actual graph instead of a plain table. The most recent/current point is
 * rendered larger so it stands out against the historical points.
 */
export const lineTrendChart = (points: TrendPoint[], options?: {
  width?: number;
  height?: number;
}): Content => {
  const width = options?.width ?? 480;
  const height = options?.height ?? 190;
  const paddingLeft = 34;
  const paddingRight = 20;
  const paddingTop = 20;
  const paddingBottom = 30;
  const chartWidth = width - paddingLeft - paddingRight;
  const chartHeight = height - paddingTop - paddingBottom;

  const n = points.length;
  const xFor = (i: number): number => (n === 1 ? paddingLeft + chartWidth / 2 : paddingLeft + (i * chartWidth) / (n - 1));
  const yFor = (value: number): number => paddingTop + chartHeight - (Math.max(0, Math.min(100, value)) / 100) * chartHeight;

  const gridLines = [0, 25, 50, 75, 100].map((pct) => {
    const y = yFor(pct);
    return `<line x1="${paddingLeft}" y1="${y.toFixed(1)}" x2="${width - paddingRight}" y2="${y.toFixed(1)}" stroke="#e4e7eb" stroke-width="1" />
      <text x="${paddingLeft - 6}" y="${(y + 3).toFixed(1)}" font-size="8" fill="#999" text-anchor="end">${pct}</text>`;
  }).join('');

  const linePoints = points.map((p, i) => `${xFor(i).toFixed(1)},${yFor(p.value).toFixed(1)}`).join(' ');

  const truncate = (label: string): string => (label.length > 14 ? `${label.slice(0, 13)}…` : label);

  const markers = points.map((p, i) => {
    const x = xFor(i);
    const y = yFor(p.value);
    const color = scoreColor(p.value);
    // First and last points in the range are marked distinctly so the reader
    // can immediately see where the trend started vs where it stands now.
    if (p.first && !p.current) {
      const s = 5;
      return `<rect x="${(x - s).toFixed(1)}" y="${(y - s).toFixed(1)}" width="${(s * 2).toFixed(1)}" height="${(s * 2).toFixed(1)}" transform="rotate(45 ${x.toFixed(1)} ${y.toFixed(1)})" fill="#fff" stroke="${color}" stroke-width="2" />
      <text x="${x.toFixed(1)}" y="${(y - s - 12).toFixed(1)}" font-size="7" fill="#666" text-anchor="middle" font-weight="bold">FIRST</text>
      <text x="${x.toFixed(1)}" y="${(y - s - 4).toFixed(1)}" font-size="8" fill="${color}" text-anchor="middle" font-weight="bold">${Math.round(p.value)}%</text>
      <text x="${x.toFixed(1)}" y="${(height - 6).toFixed(1)}" font-size="7" fill="#666" text-anchor="middle">${truncate(p.label)}</text>`;
    }
    const radius = p.current ? 6 : 4;
    const latestTag = p.current
      ? `<text x="${x.toFixed(1)}" y="${(y - radius - 13).toFixed(1)}" font-size="7" fill="#666" text-anchor="middle" font-weight="bold">LATEST</text>`
      : '';
    return `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${radius}" fill="${color}" stroke="#fff" stroke-width="1.5" />
      ${latestTag}
      <text x="${x.toFixed(1)}" y="${(y - radius - 5).toFixed(1)}" font-size="8" fill="${color}" text-anchor="middle" font-weight="bold">${Math.round(p.value)}%</text>
      <text x="${x.toFixed(1)}" y="${(height - 6).toFixed(1)}" font-size="7" fill="#666" text-anchor="middle">${truncate(p.label)}</text>`;
  }).join('');

  const svg = `<svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
    ${gridLines}
    <polyline points="${linePoints}" fill="none" stroke="#7c4dff" stroke-width="2" />
    ${markers}
  </svg>`;

  return {
    svg,
    width,
    height,
    margin: [0, 4, 0, 8] as [number, number, number, number],
  } as unknown as Content;
};

/**
 * "^ +12% vs. previous run (68%)" style delta callout - green if the score
 * improved, red if it regressed, grey if flat or if there is no previous
 * run within the selected comparison window/range to compare against.
 */
export const trendDeltaBadge = (t: Translate, current: number, previous?: number, windowLabel?: string): Content => {
  if (previous === undefined) {
    return {
      text: t('No previous run within the selected range to compare against.'),
      italics: true,
      color: '#666',
      fontSize: 9,
      margin: [0, 0, 0, 12] as [number, number, number, number],
    };
  }
  const delta = Math.round(current - previous);
  let color = '#666';
  if (delta > 0) color = REPORT_COLORS.success;
  else if (delta < 0) color = REPORT_COLORS.danger;
  let arrow = '=';
  if (delta > 0) arrow = '^';
  else if (delta < 0) arrow = 'v';
  const sign = delta > 0 ? '+' : '';
  let trendWord = t('unchanged');
  if (delta > 0) trendWord = t('improvement');
  else if (delta < 0) trendWord = t('decline');
  const commentLine: Content[] = windowLabel
    ? [{
        text: delta !== 0
          ? `${windowLabel}: ${sign}${delta}% ${trendWord}`
          : `${windowLabel}: ${t('no change')}`,
        italics: true,
        fontSize: 9,
        color: '#444',
        margin: [0, 2, 0, 0] as [number, number, number, number],
      }]
    : [];
  return {
    stack: [
      {
        text: [
          {
            text: `${arrow} ${sign}${delta}% `,
            color,
            bold: true,
            fontSize: 12,
          },
          {
            text: `${t('vs. previous run')} (${Math.round(previous)}%)`,
            color: '#666',
            fontSize: 9,
          },
        ],
      },
      ...commentLine,
    ],
    margin: [0, 0, 0, 12] as [number, number, number, number],
  };
};

/** Row of dark stat cards used on the cover page (severity / injects / techniques / phases, etc.). */
export const darkStatCards = (cards: StatCardDef[]): Content => ({
  table: {
    widths: cards.map(() => '*'),
    body: [cards.map((card, idx) => ({
      stack: [
        {
          text: card.label.toUpperCase(),
          fontSize: 7,
          bold: true,
          color: REPORT_COLORS.textOnDarkMuted,
          characterSpacing: 0.5,
        },
        {
          text: card.value,
          fontSize: 16,
          bold: true,
          color: card.color ?? REPORT_COLORS.textOnDarkPrimary,
          margin: [0, 4, 0, 0] as [number, number, number, number],
        },
      ],
      margin: [12, 10, 12, 10] as [number, number, number, number],
      border: [idx > 0, false, false, false] as [boolean, boolean, boolean, boolean],
    }))],
  },
  layout: {
    hLineWidth: () => 0,
    vLineWidth: () => 0.5,
    vLineColor: () => REPORT_COLORS.border,
    paddingLeft: () => 0,
    paddingRight: () => 0,
    paddingTop: () => 0,
    paddingBottom: () => 0,
  },
  fillColor: REPORT_COLORS.card,
  margin: [0, 20, 0, 20] as [number, number, number, number],
});

/** Row of light stat cards used inside the report body (metrics section, not the dark cover). */
export const lightStatCards = (cards: StatCardDef[]): Content => ({
  columns: cards.map(card => ({
    width: '*',
    table: {
      widths: ['*'],
      body: [[{
        stack: [
          {
            text: card.label.toUpperCase(),
            fontSize: 7,
            bold: true,
            color: '#666',
            characterSpacing: 0.5,
          },
          {
            text: card.value,
            fontSize: 18,
            bold: true,
            color: card.color ?? '#111',
            margin: [0, 4, 0, 0] as [number, number, number, number],
          },
        ],
        margin: [10, 10, 10, 10] as [number, number, number, number],
      }]],
    },
    layout: {
      hLineWidth: (i: number) => (i === 0 ? 3 : 0),
      vLineWidth: () => 0,
      hLineColor: () => card.color ?? REPORT_COLORS.accent,
      paddingLeft: () => 0,
      paddingRight: () => 0,
      paddingTop: () => 0,
      paddingBottom: () => 0,
    },
    fillColor: '#f4f6f8',
  })),
  columnGap: 10,
  margin: [0, 8, 0, 20] as [number, number, number, number],
});

/**
 * Numbered section header with a small red kicker, an accent rule and the
 * section title. Registers the section in the auto-generated table of
 * contents via pdfmake's native `tocItem` support (real page numbers, no
 * hand-maintained TOC).
 */
export const sectionHeader = (sectionNo: string, title: string, opts?: { pageBreak?: 'before' }): Content => ({
  stack: [
    {
      text: `SECTION ${sectionNo}`,
      style: 'sectionKicker',
    },
    {
      text: title,
      style: 'sectionTitle',
      // `tocItem` must live on this leaf text node (not the outer stack) —
      // pdfmake's DocPreprocessor only registers TOC entries + page-number
      // references while walking actual "Text" leaf nodes; a node whose
      // type resolves to "stack" is processed as a container and never
      // reaches that registration step, which is why the TOC used to
      // render with no entries/page numbers at all.
      tocItem: true,
      tocStyle: 'tocEntry',
      tocMargin: [0, 0, 0, 4] as [number, number, number, number],
    },
    {
      canvas: [{
        type: 'line',
        x1: 0,
        y1: 0,
        x2: 535,
        y2: 0,
        lineWidth: 1,
        lineColor: REPORT_COLORS.tableBorder,
      }],
      margin: [0, 6, 0, 12] as [number, number, number, number],
    },
  ],
  pageBreak: opts?.pageBreak,
} as unknown as Content);

/** Header bar repeated on every page after the cover (title strip + date). */
export const buildPageHeader = (reportLabel: string, dateLabel: string) => (currentPage: number): Content => {
  if (currentPage === 1) return { text: '' };
  return {
    columns: [
      {
        text: reportLabel,
        style: 'pageHeaderText',
        margin: [30, 20, 0, 0],
      },
      {
        text: dateLabel,
        style: 'pageHeaderText',
        alignment: 'right',
        margin: [0, 20, 30, 0],
      },
    ],
  };
};

/** Footer repeated on every page (Filigran/OpenAEV branding + page number). */
export const buildPageFooter = (leftText: string) => (currentPage: number, pageCount: number): Content => ({
  columns: [
    {
      text: leftText,
      alignment: 'left',
      margin: [30, 10, 0, 0],
    },
    {
      text: `${currentPage} / ${pageCount}`,
      alignment: 'right',
      margin: [0, 10, 30, 0],
    },
  ],
  style: 'footerStyle',
});

/** Full-bleed dark background for the cover page only (page 1). */
export const coverBackground = (currentPage: number, pageSize: {
  width: number;
  height: number;
}): ContentCanvas | undefined => {
  if (currentPage !== 1) return undefined;
  return {
    canvas: [{
      type: 'rect',
      x: 0,
      y: 0,
      w: pageSize.width,
      h: pageSize.height,
      color: REPORT_COLORS.navy,
    }],
  };
};

export interface CoverPageOptions {
  t: Translate;
  tlpLabel: string;
  dateLabel: string;
  kicker: string;
  titleMain: string;
  titleAccent?: string;
  subtitle: string;
  statCards: StatCardDef[];
  description: string;
  footerRight: string;
}

/** Builds the dark cover page content (to be paired with `coverBackground`). */
export const buildCoverPage = (opts: CoverPageOptions): Content[] => [
  {
    columns: [
      {
        text: opts.tlpLabel.toUpperCase(),
        color: REPORT_COLORS.textOnDarkMuted,
        fontSize: 8,
        bold: true,
        characterSpacing: 0.6,
      },
      {
        text: opts.dateLabel,
        color: REPORT_COLORS.textOnDarkMuted,
        fontSize: 8,
        alignment: 'right',
      },
    ],
    margin: [0, 0, 0, 10] as [number, number, number, number],
  },
  {
    canvas: [{
      type: 'line',
      x1: 0,
      y1: 0,
      x2: 535,
      y2: 0,
      lineWidth: 0.5,
      lineColor: REPORT_COLORS.border,
    }],
    margin: [0, 0, 0, 140] as [number, number, number, number],
  },
  {
    canvas: [{
      type: 'line',
      x1: 0,
      y1: 0,
      x2: 30,
      y2: 0,
      lineWidth: 2,
      lineColor: REPORT_COLORS.accent,
    }],
    margin: [0, 0, 0, 6] as [number, number, number, number],
  },
  {
    text: opts.kicker.toUpperCase(),
    color: REPORT_COLORS.accent,
    fontSize: 9,
    bold: true,
    characterSpacing: 0.8,
    margin: [0, 0, 0, 10] as [number, number, number, number],
  },
  {
    text: [
      {
        text: `${opts.titleMain} `,
        color: REPORT_COLORS.textOnDarkPrimary,
      },
      opts.titleAccent
        ? {
            text: opts.titleAccent,
            color: REPORT_COLORS.accent,
          }
        : '',
    ],
    fontSize: 28,
    bold: true,
    margin: [0, 0, 0, 8] as [number, number, number, number],
  } as ContentText,
  {
    text: opts.subtitle,
    color: REPORT_COLORS.textOnDarkMuted,
    fontSize: 12,
    margin: [0, 0, 0, 20] as [number, number, number, number],
  },
  {
    canvas: [{
      type: 'line',
      x1: 0,
      y1: 0,
      x2: 40,
      y2: 0,
      lineWidth: 0.5,
      lineColor: REPORT_COLORS.border,
    }],
    margin: [0, 0, 0, 20] as [number, number, number, number],
  },
  darkStatCards(opts.statCards),
  {
    text: opts.description,
    color: REPORT_COLORS.textOnDarkMuted,
    fontSize: 10,
    lineHeight: 1.4,
    margin: [0, 0, 0, 0] as [number, number, number, number],
  },
  {
    text: '',
    margin: [0, 100, 0, 0] as [number, number, number, number],
  },
  {
    canvas: [{
      type: 'line',
      x1: 0,
      y1: 0,
      x2: 535,
      y2: 0,
      lineWidth: 0.5,
      lineColor: REPORT_COLORS.border,
    }],
    margin: [0, 0, 0, 10] as [number, number, number, number],
  },
  {
    columns: [
      {
        text: 'FILIGRAN · XTM ONE PLATFORM · OPENAEV',
        color: REPORT_COLORS.textOnDarkMuted,
        fontSize: 8,
      },
      {
        text: opts.footerRight,
        color: REPORT_COLORS.textOnDarkMuted,
        fontSize: 8,
        alignment: 'right',
      },
    ],
  },
  {
    text: '',
    pageBreak: 'after',
  },
];

export interface TocPageOptions {
  t: Translate;
  tocId: string;
  intro: string;
  classificationTitle: string;
  classificationText: string;
  intelligenceTitle?: string;
  intelligenceText?: string;
}

/** Navigation / Table of Contents page, populated automatically by pdfmake from `tocItem` entries. */
export const buildTocPage = (opts: TocPageOptions): Content[] => {
  const boxes: Content[] = [calloutBox(opts.classificationTitle, {
    text: opts.classificationText,
    fontSize: 8,
    color: '#333',
  })];
  if (opts.intelligenceTitle && opts.intelligenceText) {
    boxes.push(calloutBox(opts.intelligenceTitle, {
      text: opts.intelligenceText,
      fontSize: 8,
      color: '#333',
    }));
  }
  return [
    {
      text: 'NAVIGATION',
      style: 'sectionKicker',
    },
    {
      text: opts.t('Table of Contents'),
      style: 'sectionTitle',
      margin: [0, 0, 0, 10] as [number, number, number, number],
    },
    {
      text: opts.intro,
      fontSize: 9,
      color: '#555',
      margin: [0, 0, 0, 16] as [number, number, number, number],
    },
    {
      // No custom `id` here: each report is rendered as its own standalone
      // pdfmake document (one `preprocessToc`/`preprocessText` pass per
      // report), so the implicit `_default_` toc bucket that `tocItem: true`
      // registers into (see `sectionHeader`) is unambiguous and always
      // correct - no risk of ID mismatch between the TOC node and the
      // section headers that should populate it.
      toc: { textStyle: 'tocEntry' },
    } as unknown as Content,
    {
      text: '',
      margin: [0, 16, 0, 0] as [number, number, number, number],
    },
    ...boxes,
    {
      text: '',
      pageBreak: 'after',
    },
  ];
};

/** Shared pdfmake `styles` block for both templates. */
export const reportSharedStyles: Record<string, Style> = {
  sectionKicker: {
    fontSize: 8,
    bold: true,
    color: REPORT_COLORS.accent,
    characterSpacing: 0.6,
  },
  sectionTitle: {
    fontSize: 16,
    bold: true,
    color: '#101828',
  },
  sectionSubtitle: {
    fontSize: 11,
    bold: true,
    color: '#101828',
    margin: [0, 14, 0, 6],
  },
  tableHeaderCell: {
    fontSize: 8,
    bold: true,
    color: '#ffffff',
  },
  pageHeaderText: {
    fontSize: 7,
    bold: true,
    color: '#8a94a6',
    characterSpacing: 0.4,
  },
  footerStyle: {
    fontSize: 8,
    color: '#8a94a6',
  },
  tocEntry: {
    fontSize: 9,
    color: '#333',
  },
};
