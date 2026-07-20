import { type Theme } from '@mui/material';

import type { Domain, EsAvgs, EsDomainsAvgData, EsSeries, EsSeriesData } from '../../../../../../../utils/api-types';
import { getOrderByDomain } from '../../../../../../../utils/domains/domainIcons';
import { TO_CLASSIFY } from '../../../../../../../utils/domains/domainUtils';
import { computeInjectExpectationLabel } from '../../../../../../../utils/statusUtils';
import { type IconBarElement } from '../../../../../common/domains/IconBar-model';

// The domain icon/order mapping lives in the shared utils (also used by the
// shared ItemDomains component); re-exported here for the widget consumers.
export { getDomainConfig, getIconByDomain, getOrderByDomain } from '../../../../../../../utils/domains/domainIcons';

// Extend base types to add frontend values on objects
export type EsExpectationByDomainTypeAndStatus = EsSeriesData & {
  percentage?: number;
  color?: string;
  label: string;
  key: string;
};
export type EsExpectationByDomainAndType = EsSeries & {
  data: EsExpectationByDomainTypeAndStatus[];
  status?: string;
  color: string;
  label: string;
  value: number;
};
export type EsDomainsAvgDataExtended = Omit<EsDomainsAvgData, 'data'> & {
  data: EsExpectationByDomainAndType[];
  color: string;
};
export type EsAvgsExtended = { security_domain_average: EsDomainsAvgDataExtended[] };

export const STATUS_EMPTY = 'empty';
export const STATUS_FAILURE = 'failure';
export const STATUS_WARNING = 'warning';
export const STATUS_INTERMEDIATE = 'intermediate';
export const STATUS_SUCCESS = 'success';
export const EMPTY_DATA = 'rgba(128,127,127,0.37)';
export const DEFAULT_EMPTY_EXPECTATIONS: EsExpectationByDomainAndType[] = [
  {
    label: 'prevention',
    value: -1,
    color: EMPTY_DATA,
    data: [],
  },
  {
    label: 'detection',
    value: -1,
    color: EMPTY_DATA,
    data: [],
  },
  {
    label: 'vulnerability',
    value: -1,
    color: EMPTY_DATA,
    data: [],
  },
];

export function calcPercentage(part: number, total: number): number {
  if (total <= 0) return -1;
  return (part / total) * 100;
}

export function formatPercentage(value: number, fractionDigits = 0): string {
  return `${value.toFixed(fractionDigits)}%`;
}

export const buildOrderedDomains = (items: IconBarElement[]): IconBarElement[] => {
  return [...items]
    .filter(item => item.name)
    .sort((a, b) => getOrderByDomain(a.name) - getOrderByDomain(b.name));
};

export const orderDomains = (domains: Domain[]): Domain[] => {
  return [...domains]
    .filter(domain => domain.domain_name)
    .sort((a, b) => getOrderByDomain(a.domain_name) - getOrderByDomain(b.domain_name));
};

/**
 * Define the color of the icon of a domain
 * @param data to calculate
 * @param theme to get colors values
 */
const colorByAverageForDomain = (data: EsExpectationByDomainAndType[], theme: Theme): string => {
  switch (true) {
    case data.some(expectationExtended => expectationExtended?.status === STATUS_FAILURE):
      return theme.palette.widgets.securityDomains.colors.failed;
    case data.some(expectationExtended => expectationExtended?.status === STATUS_WARNING):
      return theme.palette.widgets.securityDomains.colors.warning;
    case data.some(expectationExtended => expectationExtended?.status === STATUS_INTERMEDIATE):
      return theme.palette.widgets.securityDomains.colors.intermediate;
    case data.some(expectationExtended => expectationExtended?.status === STATUS_SUCCESS):
      return theme.palette.widgets.securityDomains.colors.success;
    default:
      return EMPTY_DATA;
  }
};

/**
 * Define the color of the icon of a line on a domain
 * @param average to calculate
 * @param theme to get colors values
 */
export const colorByAverageForExpectation = (average: number, theme: Theme): string => {
  switch (true) {
    case average < 0:
      return EMPTY_DATA;
    case average <= 25:
      return theme.palette.widgets.securityDomains.colors.failed;
    case average <= 50:
      return theme.palette.widgets.securityDomains.colors.warning;
    case average <= 75:
      return theme.palette.widgets.securityDomains.colors.intermediate;
    case average <= 100:
      return theme.palette.widgets.securityDomains.colors.success;
    default:
      return theme.palette.widgets.securityDomains.colors.unknown;
  }
};

/**
 * Case-insensitive status matcher: ES bucket keys depend on the keyword normalizer of the live
 * mapping (may be "SUCCESS" or "success"), so status comparisons must never be case-sensitive.
 */
export const isStatus = (value: string | null | undefined, status: string): boolean =>
  (value ?? '').toUpperCase() === status.toUpperCase();

/**
 * Define the colors of the percentage displayed on each lines of a domain
 * @param label to calculate
 * @param theme to get colors values
 */
export const colorByLabel = (label: string | null, theme: Theme): string => {
  if (isStatus(label, 'SUCCESS')) {
    return theme.palette.widgets.securityDomains.colors.success;
  }
  if (isStatus(label, 'FAILED')) {
    return theme.palette.widgets.securityDomains.colors.failed;
  }
  return theme.palette.widgets.securityDomains.colors.pending;
};

/**
 * Determine the status from an average
 * @param average to define
 */
export const statusByAverage = (average: number): string => {
  switch (true) {
    case average < 0:
      return STATUS_EMPTY;
    case average <= 25:
      return STATUS_FAILURE;
    case average <= 50:
      return STATUS_WARNING;
    case average <= 75:
      return STATUS_INTERMEDIATE;
    case average <= 100:
      return STATUS_SUCCESS;
    default:
      return STATUS_EMPTY;
  }
};

/**
 * Determine all percentage, color and status for a full EsSeries object
 * @param esSerie to determine
 * @param theme to get colors values
 */
const manageExpectationByDomainAndType = (esSerie: EsSeries, theme: Theme): EsExpectationByDomainAndType => {
  // Manage all data on a Serie, represent the results (success and failed) elements of a line from a domain
  const calculatedAveragesByDomainTypeAndStatus = esSerie.data?.map((expectationData) => {
    return {
      ...expectationData,
      label: expectationData.label ? computeInjectExpectationLabel(expectationData.label, esSerie.label) : '',
      percentage: expectationData.value != null && esSerie.value != null ? calcPercentage(expectationData.value, esSerie.value) : null,
      color: colorByLabel(expectationData.label ?? null, theme),
    } as EsExpectationByDomainTypeAndStatus;
  });

  // Success rate over RESOLVED expectations only (success + failed): pending/unknown docs must
  // not deflate the rate (the resilience gauges use the same denominator). Status keys are
  // matched case-insensitively (raw ES bucket keys). No resolved data -> -1 (empty state, grey).
  const success = esSerie.data
    ?.filter(d => isStatus(d.key, 'SUCCESS'))
    .reduce((acc, d) => acc + (d.value ?? 0), 0) ?? 0;
  const failed = esSerie.data
    ?.filter(d => isStatus(d.key, 'FAILED'))
    .reduce((acc, d) => acc + (d.value ?? 0), 0) ?? 0;
  const successRate = calcPercentage(success, success + failed);

  return {
    ...esSerie,
    data: calculatedAveragesByDomainTypeAndStatus ?? [],
    color: colorByAverageForExpectation(successRate, theme),
    status: statusByAverage(successRate),
  } as EsExpectationByDomainAndType;
};

/**
 * Determine all percentage, color and status for a full EsDomainsAvgData object
 * @param domainAvgs to determine
 * @param theme to get colors values
 */
const manageDomainAverage = (domainAvgs: EsDomainsAvgData, theme: Theme): EsDomainsAvgDataExtended => {
  // Manage Domain averages, represent all the lines of a domain on the widget
  const calculatedAvgsByExpectationType = domainAvgs.data?.map(esSerie =>
    manageExpectationByDomainAndType(esSerie, theme),
  );

  return {
    ...domainAvgs,
    data: calculatedAvgsByExpectationType,
    color: colorByAverageForDomain(calculatedAvgsByExpectationType, theme),
  };
};

/**
 * Determine all percentage, color and status for a full EsAvgs object
 * @param esAvgs to determine
 * @param theme to get colors values
 */
export const determinePercentage = (esAvgs: EsAvgs, theme: Theme): EsAvgsExtended => {
  // Manage Security Domain Average, represent the list of available average to display on the widget
  const calculatedAveragesBySecurityDomain = esAvgs.security_domain_average
    .filter(domainAvgs => domainAvgs.label !== TO_CLASSIFY)
    .map(domainAvgs => manageDomainAverage(domainAvgs, theme));

  return { security_domain_average: calculatedAveragesBySecurityDomain };
};
