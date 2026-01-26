import { Groups, HelpOutlined, ImportantDevices, Language, Lock, Mail, WebAsset } from '@mui/icons-material';
import { type Theme } from '@mui/material';
import { Cloud, Database } from 'mdi-material-ui';
import { type ReactElement } from 'react';

import type { EsAvgs, EsDomainsAvgData, EsSeries, EsSeriesData } from '../../../../../../../utils/api-types';
import { TO_CLASSIFY } from '../../../../../../../utils/domains/domainUtils';
import { type IconBarElement } from '../../../../../common/domains/IconBar-model';

// Extend base types to add frontend values on objects
export type EsExpectationDataExtended = EsSeriesData & {
  percentage?: number;
  color?: string;
};
export type EsExpectationExtended = EsSeries & {
  data?: EsExpectationDataExtended[];
  status?: string;
  color?: string;
};
export type EsDomainsAvgDataExtended = EsDomainsAvgData & {
  data?: EsExpectationExtended[];
  color?: string;
};
export type EsAvgsExtended = { security_domain_average: EsDomainsAvgDataExtended[] };

export const STATUS_EMPTY = 'empty';
export const STATUS_FAILURE = 'failure';
export const STATUS_WARNING = 'warning';
export const STATUS_INTERMEDIATE = 'intermediate';
export const STATUS_SUCCESS = 'success';
export const EMPTY_DATA = 'rgba(128,127,127,0.37)';
export const DEFAULT_EMPTY_EXPECTATIONS: EsExpectationExtended[] = [
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

export function getIconByDomain(name: string | undefined): ReactElement {
  switch (name) {
    case 'Endpoint':
      return <ImportantDevices fontSize="large" />;
    case 'Network':
      return <Language fontSize="large" />;
    case 'Web App':
      return <WebAsset fontSize="large" />;
    case 'E-mail Infiltration':
      return <Mail fontSize="large" />;
    case 'Data Exfiltration':
      return <Database fontSize="large" />;
    case 'URL Filtering':
      return <Lock fontSize="large" />;
    case 'Cloud':
      return <Cloud fontSize="large" />;
    case 'Tabletop':
      return <Groups fontSize="large" />;
    default:
      return <HelpOutlined fontSize="large" />;
  }
};

export function getOrderByDomain(name: string | undefined): number {
  switch (name) {
    case 'Endpoint':
      return 0;
    case 'Network':
      return 1;
    case 'Web App':
      return 2;
    case 'E-mail Infiltration':
      return 3;
    case 'Data Exfiltration':
      return 4;
    case 'URL Filtering':
      return 5;
    case 'Cloud':
      return 6;
    case 'Tabletop':
      return 7;
    default:
      return 8;
  }
};

export function calcPercentage(part: number, total: number): number {
  if (total <= 0) return -1;
  return (part / total) * 100;
}

export function formatPercentage(value: number, fractionDigits = 0): string {
  return `${value.toFixed(fractionDigits)}%`;
}

export function buildOrderedDomains(items: IconBarElement[]): IconBarElement[] {
  const orderedDomains: IconBarElement[] = [];
  for (const item of items) {
    const name = item.name;
    if (!name) continue;
    const index = getOrderByDomain(item.name);
    orderedDomains[index] = item;
  }
  return orderedDomains;
}

/**
 * Define the color of the icon of a domain
 * @param data to calculate
 * @param theme to get colors values
 */
const colorByAverageForDomain = (data: EsExpectationExtended[], theme: Theme): string => {
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
const colorByAverageForExpectation = (average: number, theme: Theme): string => {
  switch (true) {
    case average < 0:
      return EMPTY_DATA;
    case average < 25:
      return theme.palette.widgets.securityDomains.colors.failed;
    case average <= 75:
      return theme.palette.widgets.securityDomains.colors.warning;
    case average < 100:
      return theme.palette.widgets.securityDomains.colors.intermediate;
    case average === 100:
      return theme.palette.widgets.securityDomains.colors.success;
    default:
      return theme.palette.widgets.securityDomains.colors.unknown;
  }
};

/**
 * Define the colors of the percentage displayed on each lines of a domain
 * @param label to calculate
 * @param theme to get colors values
 */
export const colorByLabel = (label: string, theme: Theme): string => {
  switch (label) {
    case 'success':
      return theme.palette.widgets.securityDomains.colors.success;
    case 'failed':
      return theme.palette.widgets.securityDomains.colors.failed;
    default:
      return theme.palette.widgets.securityDomains.colors.pending;
  }
};

/**
 * Determine the status from an average
 * @param average to define
 */
export const statusByAverage = (average: number): string => {
  switch (true) {
    case average < 0:
      return STATUS_EMPTY;
    case average < 25:
      return STATUS_FAILURE;
    case average <= 75:
      return STATUS_WARNING;
    case average < 100:
      return STATUS_INTERMEDIATE;
    case average === 100:
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
const manageExpectationExtended = (esSerie: EsSeries, theme: Theme): EsExpectationExtended => {
  const esExpectationExtended: EsExpectationExtended = {
    ...esSerie,
    data: [],
  };
  let successEsExpectationDataExtended: EsExpectationDataExtended = {};

  // Manage all data on a Serie, reprensent the results (success and failed) elements of a line from a domain
  esSerie.data?.map((expectationData) => {
    const esExpectationDataExtended: EsExpectationDataExtended = { ...expectationData };

    // Calculate percentage and color of a result (failed or success) from a line of expectation on a domain
    if (expectationData.value != null && esExpectationExtended.value != null && expectationData.label != null) {
      esExpectationDataExtended.percentage = calcPercentage(expectationData.value, esExpectationExtended.value);
      esExpectationDataExtended.color = colorByLabel(expectationData.label, theme);
    }
    esExpectationExtended.data!.push(esExpectationDataExtended);

    if (esExpectationDataExtended.key === 'success') {
      successEsExpectationDataExtended = esExpectationDataExtended;
    }
  });

  // Determine the information for the icon of the expectation line of a domain, from the success value
  const successRate = successEsExpectationDataExtended.value && esSerie.value
    ? calcPercentage(successEsExpectationDataExtended.value, esSerie.value)
    : 0;
  esExpectationExtended.color = colorByAverageForExpectation(successRate, theme);
  esExpectationExtended.status = statusByAverage(successRate);
  return esExpectationExtended;
};

/**
 * Determine all percentage, color and status for a full EsDomainsAvgData object
 * @param domainAvgs to determine
 * @param theme to get colors values
 */
const manageDomainAverage = (domainAvgs: EsDomainsAvgData, theme: Theme): EsDomainsAvgDataExtended => {
  const domainAvgsExtended: EsDomainsAvgDataExtended = {
    ...domainAvgs,
    data: [],
  };

  // Manage Domain averages, represent all the lines of a domain on the widget
  domainAvgs.data?.forEach((esSerie) => {
    const esExpectationExtended = manageExpectationExtended(esSerie, theme);
    domainAvgsExtended.data!.push(esExpectationExtended);
  });

  domainAvgsExtended.color = colorByAverageForDomain(domainAvgsExtended.data ?? [], theme);
  return domainAvgsExtended;
};

/**
 * Determine all percentage, color and status for a full EsAvgs object
 * @param esAvgs to determine
 * @param theme to get colors values
 */
export const determinePercentage = (esAvgs: EsAvgs, theme: Theme): EsAvgsExtended => {
  const mappedAverage: EsAvgsExtended = {
    ...esAvgs,
    security_domain_average: [],
  };

  // Manage Security Domain Average, represent the list of available average to display on the widget
  esAvgs.security_domain_average
    .filter(domainAvgs => domainAvgs.label !== TO_CLASSIFY)
    .forEach((domainAvgs) => {
      const domainAvgsExtended = manageDomainAverage(domainAvgs, theme);
      mappedAverage.security_domain_average.push(domainAvgsExtended);
    });
  return mappedAverage;
};
