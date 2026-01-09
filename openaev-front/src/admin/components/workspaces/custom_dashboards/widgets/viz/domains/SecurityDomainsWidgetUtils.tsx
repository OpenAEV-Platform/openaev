import {  ReactElement } from 'react';
import { Groups, ImportantDevices, Language, Lock, Mail, WebAsset, HelpOutlined } from '@mui/icons-material';
import { Cloud, Database } from 'mdi-material-ui';

export function getDomainByIcon(name: string | undefined): ReactElement  {
  switch (name){
    case 'Endpoint':
      return <ImportantDevices fontSize="large"/>;
    case 'Network':
      return <Language fontSize="large"/>;
    case 'Web App':
      return <WebAsset fontSize="large"/>;
    case 'E-mail Infiltration':
      return <Mail fontSize="large"/>;
    case 'Data Exfiltration':
      return <Database fontSize="large"/>;
    case 'URL Filtering':
      return <Lock fontSize="large"/>;
    case 'Cloud':
      return <Cloud fontSize="large"/>;
    case 'Tabletop':
      return <Groups fontSize="large"/>;
    default:
      return <HelpOutlined fontSize="large"/>;
  }
};

export function calcPercentage (part: number, total: number): number  {
  if (total <= 0) return - 1;
  return (part / total) * 100;
}

export function formatPercentage (value: number , fractionDigits = 0): string {
  return `${value.toFixed(fractionDigits)}%`;
}
