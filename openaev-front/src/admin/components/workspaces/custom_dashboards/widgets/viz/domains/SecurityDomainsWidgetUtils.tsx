import { Groups, HelpOutlined, ImportantDevices, Language, Lock, Mail, WebAsset } from '@mui/icons-material';
import { type SvgIconProps, type Theme } from '@mui/material';
import { Cloud, Database } from 'mdi-material-ui';
import { type ComponentType, type CSSProperties, type ReactElement } from 'react';

import type { Domain, EsAvgs, EsDomainsAvgData, EsSeries, EsSeriesData } from '../../../../../../../utils/api-types';
import { TO_CLASSIFY } from '../../../../../../../utils/domains/domainUtils';
import { computeInjectExpectationLabel } from '../../../../../../../utils/statusUtils';
import { type IconBarElement } from '../../../../../common/domains/IconBar-model';

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

interface DomainConfig {
  icon: ComponentType<SvgIconProps>;
  order: number;
}

const DOMAIN_CONFIG: Record<string, DomainConfig> = {
  'Endpoint': {
    icon: ImportantDevices,
    order: 0,
  },
  'Network': {
    icon: Language,
    order: 1,
  },
  'Web App': {
    icon: WebAsset,
    order: 2,
  },
  'E-mail Infiltration': {
    icon: Mail,
    order: 3,
  },
  'Data Exfiltration': {
    icon: Database,
    order: 4,
  },
  'URL Filtering': {
    icon: Lock,
    order: 5,
  },
  'Cloud': {
    icon: Cloud,
    order: 6,
  },
  'Tabletop': {
    icon: Groups,
    order: 7,
  },
};

const DEFAULT_CONFIG: DomainConfig = {
  icon: HelpOutlined,
  order: 8,
};

export const getDomainConfig = (name: string | undefined): DomainConfig => {
  return DOMAIN_CONFIG[name ?? ''] ?? DEFAULT_CONFIG;
};

export const getIconByDomain = (
  name: string | undefined,
  style: CSSProperties = {},
): ReactElement => {
  const { icon: IconComponent } = getDomainConfig(name);
  return <IconComponent fontSize="large" style={style} />;
};

export const getOrderByDomain = (name: string | undefined): number => {
  return getDomainConfig(name).order;
};

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
export const colorByLabel = (label: string | null, theme: Theme): string => {
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

  // Determine the information for the icon of the expectation line of a domain, from the success value
  const successExpectationByDomainAndType = esSerie.data?.find(expectationData => expectationData.key === 'success');
  const successRate = successExpectationByDomainAndType?.value && esSerie?.value
    ? calcPercentage(successExpectationByDomainAndType.value, esSerie.value)
    : 0;

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
