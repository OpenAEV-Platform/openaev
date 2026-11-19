import { type ReactElement } from 'react';

import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import { fetchScenarioAuthorOptions } from '../../../../actions/scenarios/scenario-actions';
import { buildAuthorRows, useAuthorFacetFilter, useAuthorOptions } from '../../../../components/common/facets/ContractFacets';
import { type FacetSection, FacetSidebar } from '../../../../components/common/facets/FacetFilters';
import useFacetFilterToggle from '../../../../components/common/facets/useFacetFilterToggle';
import { type FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { useHelper } from '../../../../store';
import { type Domain, type SearchPaginationInput } from '../../../../utils/api-types';
import { TO_CLASSIFY } from '../../../../utils/domains/domainUtils';
import { type Option } from '../../../../utils/Option';
import { DOMAIN_ICON_MAP } from '../../common/domains/DomainsIcons';
import { orderDomains } from '../../workspaces/custom_dashboards/widgets/viz/domains/SecurityDomainsWidgetUtils';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED } from '../scenario/ScenarioType';

interface Facet {
  key: string;
  label: string;
  values: {
    value: string;
    label: string;
    icon?: () => ReactElement;
  }[];
}

interface Props {
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
}

// No counts: the scenario search has no facet aggregation yet.
const ScenariosSidebar = ({ searchPaginationInput, filterHelpers }: Props) => {
  const { t } = useFormatter();
  const { valuesOf, toggle } = useFacetFilterToggle(searchPaginationInput, filterHelpers);
  const domains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const authorOptions = useAuthorOptions(() => fetchScenarioAuthorOptions()
    .then(({ data }: { data: Option[] }) => data.map(option => ({
      value: option.id,
      label: option.label,
    }))));
  const author = useAuthorFacetFilter('scenario_author', searchPaginationInput.filterGroup?.filters ?? [], filterHelpers);

  const facets: Facet[] = [
    {
      key: 'scenario_domains',
      label: 'Domains',
      values: orderDomains(domains)
        .filter(domain => domain.domain_name !== TO_CLASSIFY)
        .map(domain => ({
          value: domain.domain_id,
          label: domain.domain_name,
          icon: DOMAIN_ICON_MAP[domain.domain_name],
        })),
    },
    {
      key: 'scenario_platforms',
      label: 'Platform',
      values: ['Windows', 'Linux', 'MacOS'].map(platform => ({
        value: platform,
        label: platform,
        icon: () => <PlatformIcon platform={platform} width={16} />,
      })),
    },
    {
      key: 'scenario_type',
      label: 'Type',
      values: [SCENARIO_TYPE_TIME_BASED, SCENARIO_TYPE_CHAINED].map(type => ({
        value: type,
        label: type,
      })),
    },
  ];

  const sections: FacetSection[] = [
    ...facets.map(facet => ({
      id: facet.key,
      label: t(facet.label),
      rows: facet.values.map(({ value, label, icon }) => ({
        value,
        label: t(label),
        icon,
        checked: valuesOf(facet.key).includes(value),
        onToggle: () => toggle(facet.key, value),
      })),
    })),
    {
      id: 'scenario_author',
      label: t('Author'),
      rows: buildAuthorRows({
        ...author,
        authorOptions,
        noAuthorLabel: t('No author'),
      }),
    },
  ];
  return <FacetSidebar sections={sections} />;
};

export default ScenariosSidebar;
