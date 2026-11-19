import { type FunctionComponent } from 'react';

import { type FacetSection, FacetSidebar } from '../../../../components/common/facets/FacetFilters';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { type AvailableActionOutput } from '../../../../utils/api-types';
import { DOMAIN_ICON_MAP } from '../../common/domains/DomainsIcons';
import { type AvailableActionFilters, facetCounts, type FacetKey } from './availableActions';

const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

interface Props {
  actions: AvailableActionOutput[];
  filters: AvailableActionFilters;
  onToggle: (key: FacetKey, value: string) => void;
}

const ThreatArsenalCatalogSidebar: FunctionComponent<Props> = ({ actions, filters, onToggle }) => {
  const { t } = useFormatter();
  const domainCounts = facetCounts(actions, filters, 'domains');
  const platformCounts = facetCounts(actions, filters, 'platforms');
  const domains = [...new Set(actions.flatMap(action => (action.action_domain ? [action.action_domain] : [])))].sort();

  const sections: FacetSection[] = [
    {
      id: 'domains',
      label: t('Domains'),
      rows: domains.map(domain => ({
        value: domain,
        label: t(domain),
        count: domainCounts[domain] ?? 0,
        icon: DOMAIN_ICON_MAP[domain],
        checked: filters.domains.includes(domain),
        onToggle: () => onToggle('domains', domain),
      })),
    },
    {
      id: 'platforms',
      label: t('Platform'),
      rows: PLATFORMS.map(platform => ({
        value: platform,
        label: t(platform),
        count: platformCounts[platform] ?? 0,
        icon: () => <PlatformIcon platform={platform} width={16} />,
        checked: filters.platforms.includes(platform),
        onToggle: () => onToggle('platforms', platform),
      })),
    },
  ];

  return <FacetSidebar sections={sections.filter(section => section.rows.length > 0)} />;
};

export default ThreatArsenalCatalogSidebar;
