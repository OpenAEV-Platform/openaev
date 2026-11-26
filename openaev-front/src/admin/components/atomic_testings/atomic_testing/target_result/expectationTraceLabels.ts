import { type InjectExpectationTrace } from '../../../../../utils/api-types';
import { ExpectationType } from '../../../common/injects/expectations/Expectation';

type TraceMatchType = NonNullable<InjectExpectationTrace['inject_expectation_trace_match_type']>;

const MATCH_TYPE_LABELS: Record<TraceMatchType, string> = {
  MARKER: 'Marker',
  SOURCE_IP: 'Source IP',
  TARGET_IP: 'Target IP',
};

export const traceMatchTypeLabelKey = (matchType: InjectExpectationTrace['inject_expectation_trace_match_type']): string | undefined =>
  (matchType ? MATCH_TYPE_LABELS[matchType] : undefined);

// Collector results that come with traces: the security platform recorded the attack.
const RESULTS_WITH_TRACES = ['prevented', 'detected', 'logged'];

export const hasCollectorTraces = (resultLabel: string | undefined): boolean => RESULTS_WITH_TRACES.includes(resultLabel?.toLowerCase() ?? '');

// i18n keys of the trace wording: a Logging trace is a log event, the others are alerts.
export interface TraceWording {
  one: string;
  many: string;
  column: string;
  timeColumn: string;
  show: string;
  empty: string;
}

const ALERT_WORDING: TraceWording = {
  one: 'alert',
  many: 'alerts',
  column: 'Alerts',
  timeColumn: 'Detection time',
  show: 'Show alerts',
  empty: 'No alerts have been reported by this security platform.',
};

const LOG_EVENT_WORDING: TraceWording = {
  one: 'log event',
  many: 'log events',
  column: 'Log events',
  timeColumn: 'Logging time',
  show: 'Show log events',
  empty: 'No log events have been reported by this security platform.',
};

export const traceWording = (expectationType: string | undefined): TraceWording =>
  (expectationType === ExpectationType.LOGGING ? LOG_EVENT_WORDING : ALERT_WORDING);
