import {
  Groups,
  ImportantDevices,
  Language,
  WebAsset,
} from '@mui/icons-material';
import {
  Cloud,
  Database,
  Lock,
  Mail,
} from 'mdi-material-ui';
import { type ReactElement } from 'react';

import { type Domain } from '../../../../utils/api-types';
import { type IconBarElement } from './IconBar-model';

const DOMAIN_ICON_MAP: Record<string, () => ReactElement> = {
  'Endpoint': () => <ImportantDevices />,
  'Network': () => <Language />,
  'Web App': () => <WebAsset />,
  'E-mail Infiltration': () => <Mail />,
  'Data Exfiltration': () => <Database />,
  'URL Filtering': () => <Lock />,
  'Cloud': () => <Cloud />,
  'Tabletop': () => <Groups />,
};

const buildIconBarElements = (
  domains: Domain[],
  onDomainClick: (domainId: string) => void,
  selectedDomainIds: string[] = [],
): IconBarElement[] => {
  return domains
    .filter(domain => DOMAIN_ICON_MAP[domain.domain_name])
    .filter(domain => domain.domain_name !== 'To classify')
    .map(domain => ({
      type: domain.domain_id,
      name: domain.domain_name,
      icon: DOMAIN_ICON_MAP[domain.domain_name],
      color: selectedDomainIds.includes(domain.domain_id)
        ? 'success'
        : 'default',
      function: () => onDomainClick(domain.domain_id),
    }));
};

export default buildIconBarElements;
