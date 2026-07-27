/**
 * Representative SAMPLE series for the simulation Mails distribution view.
 *
 * Rendered greyed-out (grayscale, non-interactive, "Sample"-chipped via
 * SamplePreview) while the simulation has no mail traffic yet, so the
 * distribution charts always preview exactly what they will look like once
 * mails start flowing - like every other widget of the platform. Never
 * persisted or sent anywhere.
 */

type TimePoint = {
  x: string;
  y: number;
};

// Built on demand so the sample window always ends "now".
const hoursAgo = (hours: number): string => new Date(Date.now() - hours * 3_600_000).toISOString();

const cumulative = (hours: number[], values: number[]): TimePoint[] =>
  hours.map((h, i) => ({
    x: hoursAgo(h),
    y: values[i],
  }));

// Cumulated total of sent mails (area chart). `name` is the translated
// "Total mails" series label passed by the caller.
export const sampleMailsOverTime = (name: string) => [
  {
    name,
    data: cumulative([6, 5.5, 5, 4, 3.2, 2.5, 1.5, 0.5], [2, 5, 9, 12, 16, 19, 22, 24]),
  },
];

// Cumulated mails per team (line chart). Fictive team names, consistent with
// the Execution tab sample timeline.
export const sampleMailsOverTimeByTeam = () => [
  {
    name: 'Red team',
    data: cumulative([6, 5, 4, 3, 2, 1], [1, 4, 6, 9, 11, 12]),
  },
  {
    name: 'SOC analysts',
    data: cumulative([5.5, 4.5, 3.5, 2.5, 1.5, 0.5], [1, 2, 4, 5, 7, 8]),
  },
  {
    name: 'Management',
    data: cumulative([5, 3.5, 2, 0.5], [1, 2, 3, 4]),
  },
];

// Horizontal bar samples (top teams / players / injects by mail volume).
export const sampleMailsByTeam = (name: string) => [
  {
    name,
    data: [
      {
        x: 'Red team',
        y: 12,
      },
      {
        x: 'SOC analysts',
        y: 8,
      },
      {
        x: 'Management',
        y: 4,
      },
    ],
  },
];

export const sampleMailsByPlayer = (name: string) => [
  {
    name,
    data: [
      {
        x: 'Alice Turner',
        y: 7,
      },
      {
        x: 'Marc Dubois',
        y: 6,
      },
      {
        x: 'Priya Sharma',
        y: 5,
      },
      {
        x: 'John Carter',
        y: 4,
      },
      {
        x: 'Lena Novak',
        y: 2,
      },
    ],
  },
];

export const sampleMailsByInject = (name: string) => [
  {
    name,
    data: [
      {
        x: 'Initial access - phishing payload',
        y: 9,
      },
      {
        x: 'Crisis communication update',
        y: 7,
      },
      {
        x: 'Detection drill notification',
        y: 5,
      },
      {
        x: 'Final debrief invitation',
        y: 3,
      },
    ],
  },
];
