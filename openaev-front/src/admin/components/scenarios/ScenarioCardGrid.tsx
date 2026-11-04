import { RouteOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import EmptyPlaceholder from '../../../components/common/EmptyPlaceholder';
import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import { useFormatter } from '../../../components/i18n';
import { type CatalogItemOutput } from '../../../utils/api-types';
import ScenarioCard from './ScenarioCard';
import { type ActiveScenario } from './useActiveScenarios';

interface Props {
  scenarios: ActiveScenario[];
  catalogItems: Map<string, CatalogItemOutput>;
  loading: boolean;
  onDelete: (scenarioId: string) => void;
}

const ScenarioCardGrid: FunctionComponent<Props> = ({ scenarios, catalogItems, loading, onDelete }) => {
  const { t } = useFormatter();
  // One card per catalogue scenario: its highest installed version.
  const isShownVersion = (scenario: ActiveScenario) => {
    const item = scenario.scenario_catalog_id ? catalogItems.get(scenario.scenario_catalog_id) : undefined;
    if (!item) return true;
    const highest = (item.catalog_item_versions ?? []).find(v => (item.catalog_item_installed_versions ?? []).includes(v));
    return !highest || scenario.scenario_version === highest;
  };
  const shown = scenarios.filter(isShownVersion);
  return (
    <>
      <MarketplaceGrid
        loading={loading}
        isEmpty={shown.length === 0}
        emptyState={<EmptyPlaceholder icon={<RouteOutlined />} title={t('No scenario matches these filters.')} />}
      >
        {shown.map(scenario => (
          <ScenarioCard
            key={scenario.scenario_id}
            scenario={scenario}
            catalogItem={scenario.scenario_catalog_id ? catalogItems.get(scenario.scenario_catalog_id) : undefined}
            onDelete={onDelete}
          />
        ))}
      </MarketplaceGrid>
    </>
  );
};

export default ScenarioCardGrid;
