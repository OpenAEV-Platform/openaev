import { Checkbox, Chip, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchScenarioTargets } from '../../../actions/catalog/scenario-catalog-actions';
import { createRunningExerciseFromScenario } from '../../../actions/scenarios/scenario-actions';
import MarketplaceCard from '../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../components/i18n';
import ItemSeverity from '../../../components/ItemSeverity';
import ItemTags from '../../../components/ItemTags';
import { type CatalogItemOutput, type Exercise, type Scenario } from '../../../utils/api-types';
import useScenarioPermissions from '../../../utils/permissions/useScenarioPermissions';
import ScenarioLaunchTargetsDrawer from './catalog/ScenarioLaunchTargetsDrawer';
import { scenarioCategories } from './constants';
import ScenarioPopover from './scenario/ScenarioPopover';
import ScenarioStatus from './scenario/ScenarioStatus';
import ScenarioType, { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED } from './scenario/ScenarioType';
import { severityAccent, severityLabelKey } from './scenarioSeverity';

interface Props {
  scenario: Scenario;
  catalogItem?: CatalogItemOutput;
  checked: boolean;
  anySelected: boolean;
  canManage: boolean;
  onToggleEntity: (event: MouseEvent<HTMLElement>) => void;
  onDelete: (scenarioId: string) => void;
}

const ScenarioCard: FunctionComponent<Props> = ({ scenario, catalogItem, checked, anySelected, canManage, onToggleEntity, onDelete }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const [askTargets, setAskTargets] = useState(false);
  const { canLaunch } = useScenarioPermissions(scenario.scenario_id);
  const launch = () => fetchScenarioTargets(scenario.scenario_id).then(({ data }) => {
    if (data.injects.some(inject => !inject.is_targeted)) {
      setAskTargets(true);
      return;
    }
    createRunningExerciseFromScenario(scenario.scenario_id)
      .then(({ data: exercise }: { data: Exercise }) => navigate(`/admin/simulations/${exercise.exercise_id}`));
  });
  const severity = scenario.scenario_severity;
  const accent = severityAccent(theme, severity);
  // Search rows carry these computed fields, absent from the Scenario type.
  const {
    scenario_workflow_id: workflowId,
    scenario_author_name: authorName,
  } = scenario as unknown as Record<string, string | undefined>;
  const version = scenario.scenario_version;
  const installed = catalogItem?.catalog_item_installed_scenarios ?? [];
  const updateAvailable = !!catalogItem && !(catalogItem.catalog_item_installed_versions ?? []).includes(catalogItem.catalog_item_version);
  // The card is a link: the version picker must not open the current version.
  const versionPicker = installed.length > 1 && version && (
    <span
      className="inline-flex"
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
      }}
    >
      <Select
        value={version}
        onValueChange={next => navigate(`/admin/scenarios/${installed.find(i => i.version === next)?.scenario_id}`)}
      >
        <SelectTrigger aria-label={t('Version')}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {installed.map(i => <SelectItem key={i.version} value={i.version}>{`v${i.version}`}</SelectItem>)}
        </SelectContent>
      </Select>
    </span>
  );
  const category = scenario.scenario_category;

  return (
    <>
      {askTargets && (
        <ScenarioLaunchTargetsDrawer
          scenarioId={scenario.scenario_id}
          scenarioName={scenario.scenario_name}
          onClose={() => setAskTargets(false)}
        />
      )}
      <MarketplaceCard
        accent={accent}
        testId="scenario-card"
        icon={<RouteOutlined color="primary" />}
        title={scenario.scenario_name}
        subtitle={authorName ? `${t('by')} ${authorName}` : undefined}
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
            leadingEntries={catalogItem
              ? [{
                  label: 'Launch',
                  action: () => {
                    launch();
                  },
                  userRight: canLaunch,
                }]
              : []}
          />
        )}
        chips={(
          <>
            {severity && <ItemSeverity label={t(severityLabelKey(severity))} severity={severity} />}
            {category && <Chip label={t(scenarioCategories.get(category) ?? category)} />}
            <ScenarioType type={workflowId ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED} variant="list" />
            <ScenarioStatus scenario={scenario} variant="list" />
            {version && !versionPicker && <Chip label={`v${version}`} />}
          </>
        )}
        platforms={scenario.scenario_platforms ?? []}
        footerRight={catalogItem
          ? (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
              >
                {updateAvailable && <Chip label={t('Update available')} severity="medium" />}
                {versionPicker}
              </div>
            )
          : <ItemTags variant="reduced-view" tags={scenario.scenario_tags} />}
        caption={`${t('Updated')} ${nsdt(scenario.scenario_updated_at)}`}
      />
    </>
  );
};

export default ScenarioCard;
