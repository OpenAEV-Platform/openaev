import { type ChipSeverity } from '@filigran/design-system';

import type { IocValidationOutput, IocValidationPairOutput, IocValidationSettingsInput } from '../../../utils/api-types';

export type IocValidationStatus = IocValidationOutput['ioc_validation_status'];
export type IocValidationTestKind = IocValidationSettingsInput['ioc_validation_allowed_test_kinds'][number];
export type IocValidationOutcome = NonNullable<IocValidationPairOutput['pair_outcome']>;

export const IOC_VALIDATION_BASE_URL = '/admin/ioc_validations';
export const IOC_VALIDATION_SETTINGS_URL = '/admin/settings/customization/ioc_validation';

// Results are computed when the simulation ends: polling faster would only load the API.
export const IOC_VALIDATION_POLL_INTERVAL_MS = 15_000;

export const IOC_VALIDATION_REJECT_REASON_MAX_LENGTH = 2000;

export const IOC_VALIDATION_TEST_KINDS: readonly IocValidationTestKind[] = [
  'DNS_RESOLUTION',
  'NETWORK_TRAFFIC',
  'HTTP_HEAD',
  'FILE_DROP',
  'LOG_INJECTION',
];

export const DEFAULT_IOC_VALIDATION_TEST_KINDS: IocValidationTestKind[] = ['DNS_RESOLUTION'];
export const DEFAULT_IOC_VALIDATION_NETWORK_PORT = 443;

export const IOC_VALIDATION_TEST_KIND_LABELS: Record<IocValidationTestKind, string> = {
  DNS_RESOLUTION: 'DNS resolution',
  NETWORK_TRAFFIC: 'Network traffic',
  HTTP_HEAD: 'HTTP HEAD request',
  FILE_DROP: 'Benign file drop',
  LOG_INJECTION: 'Benign log line',
};

export const IOC_VALIDATION_TEST_KIND_DESCRIPTIONS: Record<IocValidationTestKind, string> = {
  DNS_RESOLUTION: 'Resolves domain and host name indicators. No connection is made to the resolved address.',
  NETWORK_TRAFFIC: 'Opens a TCP connection to IP address indicators, or to the sinkhole when one is set, and closes it at once without sending any payload.',
  HTTP_HEAD: 'Sends an HTTP HEAD request to URL indicators through the egress proxy. No content is downloaded.',
  FILE_DROP: 'Writes a benign text file carrying the indicator file name. The real file is never downloaded or executed.',
  LOG_INJECTION: 'Writes a benign log line containing the indicator value, such as a file hash.',
};

const STATUS_LABELS: Record<IocValidationStatus, string> = {
  AWAITING_APPROVAL: 'Awaiting approval',
  RUNNING: 'Running',
  COMPLETED: 'Completed',
  PARTIAL: 'Partial',
  FAILED: 'Failed',
  REJECTED: 'Rejected',
};

// Same ladder as statusUtils: green "low" for success, blue "info" for in progress, orange "medium"
// for what needs attention, red "critical" for failure, neutral for a closed request.
const STATUS_SEVERITIES: Record<IocValidationStatus, ChipSeverity> = {
  AWAITING_APPROVAL: 'medium',
  RUNNING: 'info',
  COMPLETED: 'low',
  PARTIAL: 'medium',
  FAILED: 'critical',
  REJECTED: 'neutral',
};

const OUTCOME_LABELS: Record<IocValidationOutcome, string> = {
  PREVENTED: 'Prevented',
  DETECTED: 'Detected',
  MISSED: 'Missed',
  ERROR: 'Error',
};

// Prevented outranks detected, so the two get distinct tones.
const OUTCOME_SEVERITIES: Record<IocValidationOutcome, ChipSeverity> = {
  PREVENTED: 'low',
  DETECTED: 'info',
  MISSED: 'critical',
  ERROR: 'medium',
};

export const PENDING_OUTCOME_LABEL = 'Pending';

export const iocValidationStatusLabel = (status: IocValidationStatus): string => STATUS_LABELS[status] ?? status;

export const iocValidationStatusSeverity = (status: IocValidationStatus): ChipSeverity => STATUS_SEVERITIES[status] ?? 'neutral';

export const iocValidationOutcomeLabel = (outcome?: IocValidationOutcome | null): string => (outcome ? OUTCOME_LABELS[outcome] ?? outcome : PENDING_OUTCOME_LABEL);

export const iocValidationOutcomeSeverity = (outcome?: IocValidationOutcome | null): ChipSeverity => (outcome ? OUTCOME_SEVERITIES[outcome] ?? 'neutral' : 'neutral');

export const iocValidationTestKindLabel = (kind?: IocValidationTestKind | null): string => (kind ? IOC_VALIDATION_TEST_KIND_LABELS[kind] ?? kind : '-');

export const isAwaitingApproval = (status?: IocValidationStatus | null): boolean => status === 'AWAITING_APPROVAL';

// A request awaiting approval can be decided elsewhere, a running one gets its results from the job.
export const isPollingStatus = (status?: IocValidationStatus | null): boolean => status === 'AWAITING_APPROVAL' || status === 'RUNNING';

// The OpenCTI link comes from the request: only plain web links are rendered.
export const isWebLink = (value?: string | null): value is string => {
  if (!value) {
    return false;
  }
  try {
    const { protocol } = new URL(value);
    return protocol === 'http:' || protocol === 'https:';
  } catch {
    return false;
  }
};

const IPV4 = /^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/;
const IPV6 = /^[0-9a-f:.]+$/i;

const isIpAddress = (value: string): boolean => IPV4.test(value) || (value.includes(':') && IPV6.test(value));

export interface IocValidationSettingsErrors {
  proxy?: string;
  sinkhole?: string;
  port?: string;
}

/** Same rules as the API, so a rejected save is explained before it is sent. Messages are i18n keys. */
export const validateIocValidationSettings = (settings: IocValidationSettingsInput, portText: string): IocValidationSettingsErrors => {
  const errors: IocValidationSettingsErrors = {};
  const proxy = (settings.ioc_validation_http_proxy_url ?? '').trim();
  const sinkhole = (settings.ioc_validation_sinkhole_address ?? '').trim();
  if (proxy && !isWebLink(proxy)) {
    errors.proxy = 'The egress proxy must be an absolute http or https URL';
  } else if (!proxy && settings.ioc_validation_allowed_test_kinds.includes('HTTP_HEAD')) {
    errors.proxy = 'HTTP HEAD tests can only be allowed once an egress proxy is configured';
  }
  if (sinkhole && !isIpAddress(sinkhole)) {
    errors.sinkhole = 'The sinkhole must be an IPv4 or IPv6 address';
  }
  const port = Number(portText);
  if (!/^\d+$/.test(portText.trim()) || port < 1 || port > 65535) {
    errors.port = 'The network port must be between 1 and 65535';
  }
  return errors;
};

export interface IocValidationOutcomeCounts {
  prevented: number;
  detected: number;
  missed: number;
  error: number;
  pending: number;
}

export const countIocValidationOutcomes = (pairs: IocValidationPairOutput[]): IocValidationOutcomeCounts => pairs.reduce<IocValidationOutcomeCounts>((counts, pair) => {
  switch (pair.pair_outcome) {
    case 'PREVENTED':
      return {
        ...counts,
        prevented: counts.prevented + 1,
      };
    case 'DETECTED':
      return {
        ...counts,
        detected: counts.detected + 1,
      };
    case 'MISSED':
      return {
        ...counts,
        missed: counts.missed + 1,
      };
    case 'ERROR':
      return {
        ...counts,
        error: counts.error + 1,
      };
    default:
      return {
        ...counts,
        pending: counts.pending + 1,
      };
  }
}, {
  prevented: 0,
  detected: 0,
  missed: 0,
  error: 0,
  pending: 0,
});
