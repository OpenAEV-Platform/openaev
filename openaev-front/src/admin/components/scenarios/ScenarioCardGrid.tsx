import { CategoryOutlined, RouteOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import EmptyPlaceholder from '../../../components/common/EmptyPlaceholder';
import MarketplaceSections from '../../../components/common/marketplace/MarketplaceSections';
import { useFormatter } from '../../../components/i18n';
import { type CatalogItemOutput, type Scenario } from '../../../utils/api-types';
import { scenarioCategories } from './constants';
import ScenarioCard from './ScenarioCard';

interface Props {
  scenarios: Scenario[];
  catalogItems: Map<string, CatalogItemOutput>;
  loading: boolean;
  onDelete: (scenarioId: string) => void;
}

const ScenarioCardGrid: FunctionComponent<Props> = ({ scenarios, catalogItems, loading, onDelete }) => {
  const { t } = useFormatter();
  // One card per catalogue scenario: its highest installed version.
  const isShownVersion = (scenario: Scenario) => {
    const item = scenario.scenario_catalog_id ? catalogItems.get(scenario.scenario_catalog_id) : undefined;
    if (!item) return true;
    const highest = (item.catalog_item_versions ?? []).find(v => (item.catalog_item_installed_versions ?? []).includes(v));
    return !highest || scenario.scenario_version === highest;
  };
  const shown = scenarios.filter(isShownVersion);
  return (
    <>
      <MarketplaceSections
        items={shown}
        sectionKey={scenario => scenario.scenario_category}
        sectionLabel={category => (category ? t(scenarioCategories.get(category) ?? category) : t('Other'))}
        sectionIcon={<CategoryOutlined />}
        loading={loading}
        emptyState={<EmptyPlaceholder icon={<RouteOutlined />} title={t('No scenario matches these filters.')} />}
        renderItem={scenario => (
          <ScenarioCard
            key={scenario.scenario_id}
            scenario={scenario}
            catalogItem={scenario.scenario_catalog_id ? catalogItems.get(scenario.scenario_catalog_id) : undefined}
            onDelete={onDelete}
          />
        )}
      />
    </>
  );
};

export default ScenarioCardGrid;
