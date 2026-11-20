import { type FunctionComponent, type ReactElement } from 'react';

import { type FacetSection, FacetSidebar } from '../../../../components/common/facets/FacetFilters';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { scenarioCategories } from '../constants';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, ScenarioTypeIcon, type ScenarioTypeValue } from '../scenario/ScenarioType';
import { type CatalogScenarioFilters, facetCounts, type FacetKey, facetValues } from './catalogScenarioFilters';

interface Facet {
  key: FacetKey;
  label: string;
  values: string[];
  valueLabel?: (value: string) => string;
  icon?: (value: string) => () => ReactElement;
}

interface Props {
  scenarios: CatalogScenarioOutput[];
  filters: CatalogScenarioFilters;
  onToggle: (key: FacetKey, value: string) => void;
}

const ScenariosCatalogSidebar: FunctionComponent<Props> = ({ scenarios, filters, onToggle }) => {
  const { t } = useFormatter();
  const facets: Facet[] = [
    {
      key: 'types',
      label: 'Type',
      values: [SCENARIO_TYPE_TIME_BASED, SCENARIO_TYPE_CHAINED],
      icon: type => () => <ScenarioTypeIcon type={type as ScenarioTypeValue} />,
    },
    {
      key: 'platforms',
      label: 'Platform',
      values: ['Windows', 'Linux', 'MacOS'],
      icon: platform => () => <PlatformIcon platform={platform} width={16} />,
    },
    {
      key: 'categories',
      label: 'Category',
      values: facetValues(scenarios, 'categories'),
      valueLabel: category => scenarioCategories.get(category) ?? category,
    },
  ];

  const sections: FacetSection[] = facets.map(({ key, label, values, valueLabel, icon }) => {
    const counts = facetCounts(scenarios, filters, key);
    return {
      id: key,
      label: t(label),
      rows: values.map(value => ({
        value,
        label: t(valueLabel?.(value) ?? value),
        count: counts[value] ?? 0,
        icon: icon?.(value),
        checked: filters[key].includes(value),
        onToggle: () => onToggle(key, value),
      })),
    };
  });

  return <FacetSidebar sections={sections.filter(section => section.rows.length > 0)} />;
};

export default ScenariosCatalogSidebar;
