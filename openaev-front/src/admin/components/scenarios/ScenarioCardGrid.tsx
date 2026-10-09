import { RouteOutlined } from '@mui/icons-material';
import { type FunctionComponent, type MouseEvent } from 'react';

import EmptyPlaceholder from '../../../components/common/EmptyPlaceholder';
import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import { useFormatter } from '../../../components/i18n';
import { type Scenario } from '../../../utils/api-types';
import ToolBar from '../common/ToolBar';
import ScenarioCard from './ScenarioCard';

interface Props {
  scenarios: Scenario[];
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
        isEmpty={scenarios.length === 0}
        emptyState={<EmptyPlaceholder icon={<RouteOutlined />} title={t('No scenario matches these filters.')} />}
      >
        {scenarios.map(scenario => (
          <ScenarioCard
            key={scenario.scenario_id}
            scenario={scenario}
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
