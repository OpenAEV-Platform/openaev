import { type FunctionComponent } from 'react';

import { type FacetSection, FacetSidebar } from '../../../../components/common/facets/FacetFilters';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { type CatalogPayloadOutput } from '../../../../utils/api-types';
import { DOMAIN_ICON_MAP } from '../../common/domains/DomainsIcons';
import { type CatalogPayloadFilters, facetCounts, type FacetKey } from './catalogPayloadFilters';

const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

interface Props {
  payloads: CatalogPayloadOutput[];
  filters: CatalogPayloadFilters;
  onToggle: (key: FacetKey, value: string) => void;
}

const ThreatArsenalCatalogSidebar: FunctionComponent<Props> = ({ payloads, filters, onToggle }) => {
  const { t } = useFormatter();
  const domainCounts = facetCounts(payloads, filters, 'domains');
  const platformCounts = facetCounts(payloads, filters, 'platforms');
  const domains = [...new Set(payloads.flatMap(payload => (payload.catalog_payload_domain ? [payload.catalog_payload_domain] : [])))].sort();

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
