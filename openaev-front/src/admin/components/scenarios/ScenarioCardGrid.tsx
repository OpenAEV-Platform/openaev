import { RouteOutlined } from '@mui/icons-material';
import { type FunctionComponent, type MouseEvent } from 'react';

import EmptyPlaceholder from '../../../components/common/EmptyPlaceholder';
import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import { useFormatter } from '../../../components/i18n';
import { type CatalogItemOutput, type Scenario } from '../../../utils/api-types';
import ToolBar from '../common/ToolBar';
import ScenarioCard from './ScenarioCard';

interface Props {
  scenarios: Scenario[];
  catalogItems: Map<string, CatalogItemOutput>;
  loading: boolean;
  canManage: boolean;
  numberOfSelectedElements: number;
  isSelected: (scenario: Scenario) => boolean;
  onToggleEntity: (scenario: Scenario, event: MouseEvent<HTMLElement>) => void;
  onClearSelectedElements: () => void;
  onBulkDelete: () => void;
  onDelete: (scenarioId: string) => void;
}

const ScenarioCardGrid: FunctionComponent<Props> = ({
  scenarios,
  catalogItems,
  loading,
  canManage,
  numberOfSelectedElements,
  isSelected,
  onToggleEntity,
  onClearSelectedElements,
  onBulkDelete,
  onDelete,
}) => {
  const { t } = useFormatter();
  // One card per catalogue scenario: its highest installed version, the others in its picker.
  const isShownVersion = (scenario: Scenario) => {
    const item = scenario.scenario_catalog_id ? catalogItems.get(scenario.scenario_catalog_id) : undefined;
    if (!item) return true;
    const highest = (item.catalog_item_versions ?? []).find(v => (item.catalog_item_installed_versions ?? []).includes(v));
    return !highest || scenario.scenario_version === highest;
  };
  const shown = scenarios.filter(isShownVersion);
  return (
    <>
      {numberOfSelectedElements > 0 && (
        <ToolBar
          numberOfSelectedElements={numberOfSelectedElements}
          handleClearSelectedElements={onClearSelectedElements}
          handleBulkDelete={onBulkDelete}
          canManage={canManage}
          deleteConfirmationSingular={t('Do you want to delete this scenario?')}
          deleteConfirmationPlural={t('Do you want to delete these {count} scenarios?', { count: String(numberOfSelectedElements) })}
        />
      )}
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
            checked={isSelected(scenario)}
            anySelected={numberOfSelectedElements > 0}
            canManage={canManage}
            onToggleEntity={event => onToggleEntity(scenario, event)}
            onDelete={onDelete}
          />
        ))}
      </MarketplaceGrid>
    </>
  );
};

export default ScenarioCardGrid;
