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
import { TO_CLASSIFY } from '../../../../utils/domains/domainUtils';
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
  domainCounts: Record<string, number>,
): IconBarElement[] => {
  return domains
    .filter(domain => domain.domain_name !== TO_CLASSIFY && DOMAIN_ICON_MAP[domain.domain_name])
    .map((domain) => {
      return {
        type: domain.domain_id,
        name: domain.domain_name,
        count: domainCounts[domain.domain_id] ?? 0,
        icon: DOMAIN_ICON_MAP[domain.domain_name],
        color: selectedDomainIds.includes(domain.domain_id) ? 'success' : 'default',
        function: () => onDomainClick(domain.domain_id),
      };
    });
};
export default buildIconBarElements;
