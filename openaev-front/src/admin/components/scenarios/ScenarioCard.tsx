import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchScenarioTargets } from '../../../actions/catalog/scenario-catalog-actions';
import { createRunningExerciseFromScenario } from '../../../actions/scenarios/scenario-actions';
import MarketplaceCard from '../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../components/i18n';
import ItemSeverity from '../../../components/ItemSeverity';
import { type CatalogItemOutput, type Exercise, type Scenario } from '../../../utils/api-types';
import handle from '../../../utils/period/Period';
import useScenarioPermissions from '../../../utils/permissions/useScenarioPermissions';
import ScenarioLaunchTargetsDrawer from './catalog/ScenarioLaunchTargetsDrawer';
import { scenarioCategories } from './constants';
import ScenarioPopover from './scenario/ScenarioPopover';
import ScenarioStatus from './scenario/ScenarioStatus';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, ScenarioTypeIcon } from './scenario/ScenarioType';
import ScenarioUpdateDialog from './scenario/versions/ScenarioUpdateDialog';
import { severityAccent, severityLabelKey } from './scenarioSeverity';

interface Props {
  scenario: Scenario;
  catalogItem?: CatalogItemOutput;
  onDelete: (scenarioId: string) => void;
}

const ScenarioCard: FunctionComponent<Props> = ({ scenario, catalogItem, onDelete }) => {
  const { t, nsd, locale } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const [askTargets, setAskTargets] = useState(false);
  const [updating, setUpdating] = useState(false);
  const { canLaunch, canManage } = useScenarioPermissions(scenario.scenario_id);
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
    scenario_last_run_at: lastRunAt,
  } = scenario as unknown as Record<string, string | undefined>;
  const version = scenario.scenario_version;
  const recurrence = useMemo(() => {
    const cron = scenario.scenario_recurrence ? handle(scenario.scenario_recurrence) : undefined;
    return cron?.isValid() ? cron.toTranslatableStringArray(locale).map(element => t(element)).join(' ') : undefined;
  }, [scenario.scenario_recurrence, locale]);
  const updateAvailable = !!catalogItem && !(catalogItem.catalog_item_installed_versions ?? []).includes(catalogItem.catalog_item_version);
  const category = scenario.scenario_category;

  return (
    <>
      {updating && catalogItem && (
        <ScenarioUpdateDialog
          scenarioId={scenario.scenario_id}
          version={catalogItem.catalog_item_version}
          onClose={() => setUpdating(false)}
          onApplied={() => navigate(`/admin/scenarios/${scenario.scenario_id}`)}
        />
      )}
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
        icon={<ScenarioTypeIcon type={workflowId ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED} />}
        title={scenario.scenario_name}
        overline={category ? t(scenarioCategories.get(category) ?? category) : undefined}
        subtitle={authorName ? `${t('by')} ${authorName}` : undefined}
        description={scenario.scenario_description ?? undefined}
        linkTo={`/admin/scenarios/${scenario.scenario_id}`}
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
                }, ...(updateAvailable
                  ? [{
                      label: 'Update',
                      action: () => setUpdating(true),
                      userRight: canManage,
                    }]
                  : [])]
              : []}
          />
        )}
        chips={(
          <>
            {severity && <ItemSeverity label={t(severityLabelKey(severity))} severity={severity} />}
          </>
        )}
        platforms={scenario.scenario_platforms ?? []}
        footerRight={(
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
          >
            {updateAvailable && <Chip label={t('Update available')} severity="medium" />}
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-flex">
                  <ScenarioStatus scenario={scenario} variant="list" />
                </span>
              </TooltipTrigger>
              <TooltipContent>{recurrence ?? t('Not scheduled')}</TooltipContent>
            </Tooltip>
          </div>
        )}
        caption={[
          version ? `v${version}` : null,
          lastRunAt ? `${t('Last run')} ${nsd(lastRunAt)}` : t('Never run'),
        ].filter(Boolean).join(' · ')}
      />
    </>
  );
};

export default ScenarioCard;
