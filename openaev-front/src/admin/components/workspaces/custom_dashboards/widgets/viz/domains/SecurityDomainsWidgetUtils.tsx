import { Groups, HelpOutlined, ImportantDevices, Language, Lock, Mail, WebAsset } from '@mui/icons-material';
import { Cloud, Database } from 'mdi-material-ui';
import { type ReactElement } from 'react';

import { type IconBarElement } from '../../../../../common/domains/IconBar-model';

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
