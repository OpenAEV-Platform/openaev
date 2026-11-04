import { HelpOutlineOutlined } from '@mui/icons-material';
import { type ReactElement } from 'react';

import { type Domain } from '../../../utils/api-types';
import { getOrderByDomain } from '../../../utils/domains/domainIcons';
import { TO_CLASSIFY } from '../../../utils/domains/domainUtils';
import { DOMAIN_ICON_MAP } from '../common/domains/DomainsIcons';

// Same order as the Threat Arsenal: by name, the first one is the primary domain.
export const sortedDomains = (domains: Domain[]): Domain[] => domains
  .filter(domain => domain.domain_name !== TO_CLASSIFY)
  .sort((a, b) => a.domain_name.localeCompare(b.domain_name));

// The Domains filter lists them in the Threat Arsenal order.
export const facetDomains = (domains: Domain[]): Domain[] => sortedDomains(domains)
  .sort((a, b) => getOrderByDomain(a.domain_name) - getOrderByDomain(b.domain_name));

export const domainIcon = (name: string | undefined): ReactElement => (DOMAIN_ICON_MAP[name ?? ''] ?? (() => <HelpOutlineOutlined />))();
