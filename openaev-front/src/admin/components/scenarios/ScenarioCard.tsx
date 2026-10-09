import { Checkbox, Chip } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent } from 'react';

import MarketplaceCard from '../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../components/i18n';
import ItemSeverity from '../../../components/ItemSeverity';
import ItemTags from '../../../components/ItemTags';
import { type Scenario } from '../../../utils/api-types';
import { scenarioCategories } from './constants';
import ScenarioPopover from './scenario/ScenarioPopover';
import ScenarioStatus from './scenario/ScenarioStatus';
import ScenarioType, { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED } from './scenario/ScenarioType';
import { severityAccent, severityLabelKey } from './scenarioSeverity';

interface Props {
  scenario: Scenario;
  checked: boolean;
  anySelected: boolean;
  canManage: boolean;
  onToggleEntity: (event: MouseEvent<HTMLElement>) => void;
  onDelete: (scenarioId: string) => void;
}

const ScenarioCard: FunctionComponent<Props> = ({ scenario, checked, anySelected, canManage, onToggleEntity, onDelete }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const severity = scenario.scenario_severity;
  const accent = severityAccent(theme, severity);
  const workflowId = (scenario as unknown as Record<string, unknown>).scenario_workflow_id;
  const category = scenario.scenario_category;

  return (
    <MarketplaceCard
      accent={accent}
      testId="scenario-card"
      icon={<RouteOutlined color="primary" />}
      title={scenario.scenario_name}
      linkTo={`/admin/scenarios/${scenario.scenario_id}`}
      topLeft={canManage && (
        <span
          className="inline-flex"
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            onToggleEntity(event);
          }}
        >
          <Checkbox checked={checked} aria-label={scenario.scenario_name} />
        </span>
      )}
      topLeftVisible={anySelected || checked}
      topRight={(
        <ScenarioPopover
          scenario={scenario}
          actions={['Duplicate', 'Export', 'Delete']}
          onDelete={onDelete}
          inList
        />
      )}
      chips={(
        <>
          {severity && <ItemSeverity label={t(severityLabelKey(severity))} severity={severity} />}
          {category && <Chip label={t(scenarioCategories.get(category) ?? category)} />}
          <ScenarioType type={workflowId ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED} variant="list" />
          <ScenarioStatus scenario={scenario} variant="list" />
        </>
      )}
      platforms={scenario.scenario_platforms ?? []}
      footerRight={<ItemTags variant="reduced-view" tags={scenario.scenario_tags} />}
      caption={`${t('Updated')} ${nsdt(scenario.scenario_updated_at)}`}
    />
  );
};

export default ScenarioCard;
