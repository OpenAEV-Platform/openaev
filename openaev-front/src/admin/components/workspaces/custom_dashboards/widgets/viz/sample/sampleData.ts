import { type EsSeries } from '../../../../../../../utils/api-types';

/**
 * Representative SAMPLE datasets for dashboard widgets.
 *
 * Rendered (greyed, non-interactive) when a widget has no real data yet, so the
 * dashboard always previews exactly what it will look like once simulations are
 * running and reporting. Clearly labelled "Sample" in the UI and replaced
 * automatically by live data; never persisted or sent anywhere.
 */

const bucket = (label: string, value: number) => ({
  label,
  value,
  key: label,
});

export const isSeriesEmpty = (series: EsSeries[] | undefined | null): boolean => {
  if (!series || series.length === 0) return true;
  return series.every(s => !s.data || s.data.length === 0 || s.data.every(d => (d.value ?? 0) === 0));
};

// -- APEX FORMAT HELPERS (charts receive series already converted to ApexCharts shape) --

export type ApexSerieData = {
  x?: string;
  y?: number;
  meta?: string;
};

export const isApexSeriesEmpty = (series: ApexAxisChartSeries | undefined | null): boolean => {
  if (!series || series.length === 0) return true;
  return series.every((s) => {
    const data = (s.data ?? []) as ApexSerieData[];
    return data.length === 0 || data.every(d => (d.y ?? 0) === 0);
  });
};

export const toApexSeries = (esSeries: EsSeries[]): ApexAxisChartSeries => esSeries.map(s => ({
  name: s.label ?? '',
  data: (s.data ?? []).map(d => ({
    x: d.label ?? '',
    y: d.value ?? 0,
    meta: d.key ?? '',
  })),
})) as ApexAxisChartSeries;

// -- STATUS BREAKDOWN (donut on inject_expectation_status) --
export const sampleStatusSeries: EsSeries[] = [{
  label: '',
  data: [
    bucket('SUCCESS', 64),
    bucket('FAILED', 27),
    bucket('PENDING', 9),
  ],
}];

// -- SECURITY PLATFORMS (bars / radar on base_security_platforms_side) --
export const samplePlatformsSeries: EsSeries[] = [
  {
    label: 'Not Detected',
    data: [
      bucket('EDR', 14),
      bucket('SIEM', 22),
      bucket('Firewall', 9),
      bucket('Email Gateway', 17),
      bucket('Web Proxy', 12),
      bucket('Cloud', 19),
    ],
  },
  {
    label: 'Not Prevented',
    data: [
      bucket('EDR', 9),
      bucket('SIEM', 16),
      bucket('Firewall', 6),
      bucket('Email Gateway', 11),
      bucket('Web Proxy', 8),
      bucket('Cloud', 13),
    ],
  },
];

// -- TTPs (horizontal bars on base_attack_patterns_side) --
export const sampleTtpSeries: EsSeries[] = [
  {
    label: 'Detected TTPs',
    data: [
      bucket('T1059 Command and Scripting Interpreter', 32),
      bucket('T1055 Process Injection', 26),
      bucket('T1003 OS Credential Dumping', 21),
      bucket('T1021 Remote Services', 17),
      bucket('T1547 Boot or Logon Autostart', 12),
    ],
  },
  {
    label: 'Prevented TTPs',
    data: [
      bucket('T1059 Command and Scripting Interpreter', 24),
      bucket('T1055 Process Injection', 19),
      bucket('T1003 OS Credential Dumping', 15),
      bucket('T1021 Remote Services', 11),
      bucket('T1547 Boot or Logon Autostart', 8),
    ],
  },
];

// -- TEMPORAL (line / vertical bars, weekly interval) --
const weeklyDates = (): string[] => {
  const dates: string[] = [];
  const now = new Date();
  for (let i = 7; i >= 0; i -= 1) {
    const d = new Date(now);
    d.setDate(d.getDate() - i * 7);
    dates.push(d.toISOString());
  }
  return dates;
};

export const sampleTemporalSeries = (label1: string, label2?: string): EsSeries[] => {
  const dates = weeklyDates();
  const values1 = [4, 7, 5, 9, 12, 8, 14, 11];
  const values2 = [2, 5, 3, 7, 9, 6, 10, 7];
  const series: EsSeries[] = [{
    label: label1,
    data: dates.map((d, i) => ({
      label: d,
      value: values1[i],
      key: d,
    })),
  }];
  if (label2) {
    series.push({
      label: label2,
      data: dates.map((d, i) => ({
        label: d,
        value: values2[i],
        key: d,
      })),
    });
  }
  return series;
};

// -- EXPOSURE SCORE (structural histogram on inject_expectation_status, 2 series SUCCESS / FAILED over inject_expectation_type) --
export const sampleExposureSeries: EsSeries[] = [
  {
    label: 'SUCCESS',
    data: [
      bucket('PREVENTION', 58),
      bucket('DETECTION', 44),
      bucket('VULNERABILITY', 23),
    ],
  },
  {
    label: 'FAILED',
    data: [
      bucket('PREVENTION', 22),
      bucket('DETECTION', 38),
      bucket('VULNERABILITY', 17),
    ],
  },
];

// -- POSTURE RADAR (2 series SUCCESS / FAILED over a structural field) --
export const sampleRadarSeries: EsSeries[] = [
  {
    label: 'SUCCESS',
    data: [
      bucket('EDR', 42),
      bucket('SIEM', 31),
      bucket('Firewall', 47),
      bucket('Email Gateway', 26),
      bucket('Web Proxy', 35),
      bucket('Cloud', 22),
    ],
  },
  {
    label: 'FAILED',
    data: [
      bucket('EDR', 12),
      bucket('SIEM', 25),
      bucket('Firewall', 8),
      bucket('Email Gateway', 21),
      bucket('Web Proxy', 14),
      bucket('Cloud', 24),
    ],
  },
];
