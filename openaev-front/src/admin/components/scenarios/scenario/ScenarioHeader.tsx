import {
  ComputerOutlined,
  DashboardCustomizeOutlined,
  EmojiEventsOutlined,
  GroupsOutlined,
  HubOutlined,
  LanOutlined,
  NewspaperOutlined,
  PersonOutlined,
  PlayArrowOutlined,
  RouteOutlined,
  Stop,
  TrackChangesOutlined,
  TuneOutlined,
  UpdateOutlined,
} from '@mui/icons-material';
import { alpha, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type Dispatch, type SetStateAction, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';

import { type AutonomousRun } from '../../../../actions/autonomous/autonomous-types';
import type { WorkflowConfigurationHelper } from '../../../../actions/chaining/workflow-helper';
import { fetchScenarioChallenges } from '../../../../actions/challenge-action';
import { fetchScenarioArticles } from '../../../../actions/channels/article-action';
import { type ArticlesHelper } from '../../../../actions/channels/article-helper';
import { type ChallengeHelper } from '../../../../actions/helper';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import {
  createRunningExerciseFromScenario,
  dismissScenarioExpectationsDrift,
  fetchScenarioExpectationsDrift,
  fetchScenarioTeams,
  realignScenarioExpectations,
  searchScenarioHealthcheks,
  updateScenarioRecurrence,
} from '../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { fetchScenarioInjectsSimple } from '../../../../actions/scenarios/scenario-inject-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import { DetailHero, HeroStat } from '../../../../components/common/detail/EntityDetailCommon';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemSeverity from '../../../../components/ItemSeverity';
import { SIMULATION_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import {
  type Article,
  type Challenge,
  type Exercise,
  type ExpectationsDriftOutput,
  type HealthCheck,
  type Inject,
  type Scenario,
  type Team,
} from '../../../../utils/api-types';
import { MESSAGING$, useQueryParameter } from '../../../../utils/Environment';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { type Cron } from '../../../../utils/period/Cron';
import handle from '../../../../utils/period/Period';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { truncate } from '../../../../utils/String';
import { isFeatureEnabled } from '../../../../utils/utils';
import AutonomousRunControls from '../../autonomous/AutonomousRunControls';
import AutonomousRunStatusChip from '../../autonomous/AutonomousRunStatusChip';
import { isAutonomousRunActive } from '../../autonomous/autonomousStatus';
import HealthcheckIndicator from '../../common/healthchecks/HealthcheckIndicator';
import ExpectationsDriftIndicator from '../../common/injects/expectations/ExpectationsDriftIndicator';
import { countDistinctInjectTargets } from '../../common/injects/utils';
import SchedulingDialog from '../../common/scheduling/SchedulingDialog';
import TriggerSubscribeButton from '../../profile/triggers/TriggerSubscribeButton';
import EntityReportsPanel from '../../reporting/EntityReportsPanel';
import { CONTEXTUAL_ENTITY_WIDGET_IDS, contextualResultsUrl } from '../../workspaces/custom_dashboards/results/contextualWidgets';
import ScenarioConfiguration from './ScenarioConfiguration';
import ScenarioPopover from './ScenarioPopover';

interface ScenarioHeaderProps {
  setOpenInstantiateSimulationAndStart: Dispatch<SetStateAction<boolean>>;
  openInstantiateSimulationAndStart: boolean;
  // Present when this scenario is an autonomous (AI-driven) run: the manual launch / scheduling /
  // scope controls are hidden and replaced with autonomous pause / resume / stop controls that act
  // on the run and its single underlying simulation.
  autonomousRun?: AutonomousRun | null;
  onAutonomousRunUpdate?: (run: AutonomousRun) => void;
}

const ScenarioHeader = ({
  openInstantiateSimulationAndStart,
  setOpenInstantiateSimulationAndStart,
  autonomousRun = null,
  onAutonomousRunUpdate,
}: ScenarioHeaderProps) => {
  // Standard hooks
  const { t, locale, fld } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const [openScenarioAssistantQueryParam] = useQueryParameter(['openScenarioAssistant']);
  const { canLaunch, canManage } = useScenarioPermissions(scenarioId);

  const [openConfiguration, setOpenConfiguration] = useState(false);
  const [openScheduling, setOpenScheduling] = useState(false);
  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);
  const [expectationsDrift, setExpectationsDrift] = useState<ExpectationsDriftOutput | null>(null);

  // Preserve the deep link that used to open the assistant drawer: it now
  // routes to the dedicated full-page assistant.
  useEffect(() => {
    if (openScenarioAssistantQueryParam === 'true') {
      navigate(`/admin/scenarios/${scenarioId}/assistant`, { replace: true });
    }
  }, [openScenarioAssistantQueryParam, scenarioId]);
  // Fetching data
  const { scenario, challenges, injects, teams, articles }: {
    scenario: Scenario;
    challenges: Challenge[];
    injects: Inject[];
    teams: Team[];
    articles: Article[];
  } = useHelper((helper: ScenariosHelper & ChallengeHelper & InjectHelper & TeamsHelper & ArticlesHelper) => ({
    scenario: helper.getScenario(scenarioId),
    challenges: helper.getScenarioChallenges(scenarioId),
    injects: helper.getScenarioInjects(scenarioId),
    teams: helper.getScenarioTeams(scenarioId),
    articles: helper.getScenarioArticles(scenarioId),
  }));

  // Challenges are authored inside injects (no configuration tab): as soon as
  // at least one inject uses a challenge, expose the player-facing preview
  // right in the hero. Injects (lightweight view), teams and articles feed the
  // usage-aware hero stats: the GET /scenarios/{id} ScenarioOutput DTO carries
  // no scenario_injects / scenario_teams / scenario_articles relations, so
  // counting those entity fields would always render 0 after a reload.
  useDataLoader(() => {
    dispatch(fetchScenarioChallenges(scenarioId));
    dispatch(fetchScenarioInjectsSimple(scenarioId));
    dispatch(fetchScenarioTeams(scenarioId));
    dispatch(fetchScenarioArticles(scenarioId));
  });
  const hasChallenges = challenges.length > 0;

  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  // isFeatureEnabled reads the store through a hook, so it must stay at render scope: calling it
  // from an event handler throws and silently aborts the handler mid-way.
  const isAttackPathEnabled = isFeatureEnabled('ATTACK_PATH');
  const scenarioWorkflowId = (scenario as unknown as Record<string, unknown>).scenario_workflow_id as string | undefined;
  const isScenarioChaining = isChainingFeatureEnabled && !!scenarioWorkflowId;
  const { workflowConfiguration } = useHelper((helper: WorkflowConfigurationHelper) => ({
    workflowConfiguration: scenarioWorkflowId
      ? helper.getWorkflowConfiguration(scenarioWorkflowId)
      : undefined,
  }));
  const isAutonomous = !!autonomousRun;
  const autonomousStatus = autonomousRun?.autonomous_run_status;
  // The run drives its single simulation: deleting the scenario tears both down, so it is only
  // allowed once the run has stopped (terminal). While it is still live (created / running /
  // paused / waiting for input) the Delete entry stays visible but disabled with a tooltip.
  const isAutonomousActive = isAutonomousRunActive(autonomousRun);
  // Overflow CRUD entries: an autonomous scenario is never duplicated by hand (the AI owns its
  // attack-path logic), but its metadata - name, description, tags, severity, category - stays
  // freely editable, so Update / Delete / Export are offered.
  let scenarioPopoverActions: ('Duplicate' | 'Update' | 'Delete' | 'Export')[] = ['Duplicate', 'Update', 'Delete', 'Export'];
  if (isAutonomous) {
    scenarioPopoverActions = ['Update', 'Delete', 'Export'];
  } else if (isScenarioChaining) {
    scenarioPopoverActions = ['Update', 'Delete', 'Export'];
  }
  const isScopeMissing = isScenarioChaining
    && healthchecks.some((hc: HealthCheck) => hc.type === ('SCOPE_DEFINITION' as HealthCheck['type']) && hc.detail === 'EMPTY');

  // Local
  const ended = scenario.scenario_recurrence_end && new Date(scenario.scenario_recurrence_end).getTime() < new Date().getTime();
  const isScheduled = !!scenario.scenario_recurrence;

  // Headline stats surfaced right in the hero so they are visible on every
  // tab. The hero adapts to how the scenario is actually built: injects and
  // simulations are always shown, while the people dimension (teams,
  // players), the technical dimension (targeted assets, asset groups) and the
  // content dimension (media pressure, challenges) only appear when actually
  // used - a tabletop reads people-first, a technical scenario reads
  // assets-first, and a mixed one shows both.
  const injectsCount = injects?.length ?? 0;
  const simulationsCount = scenario.scenario_exercises?.length ?? 0;
  const teamsCount = teams?.length ?? 0;
  const playersCount = scenario.scenario_all_users_number ?? scenario.scenario_users_number ?? 0;
  const articlesCount = articles?.length ?? 0;
  const { assets: assetsCount, assetGroups: assetGroupsCount, assetGroupIds } = countDistinctInjectTargets(injects);

  // Countable stats drill down to the full-page results explorer (the same
  // one the dashboards use), scoped to this scenario. Players, media pressure
  // and challenges are not indexed in the engine, so they stay static.
  const statResultsUrl = (entity: string, filterValuesMap?: Record<string, string[] | undefined>) =>
    contextualResultsUrl(
      CONTEXTUAL_ENTITY_WIDGET_IDS[entity],
      'scenario',
      scenarioId,
      location.pathname + location.search,
      filterValuesMap,
    );

  useEffect(() => {
    searchScenarioHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [scenarioId, scenario, workflowConfiguration]);

  // Expectation drift between the injector contract templates and the inject
  // content - recomputed when the scenario or its inject set changes.
  useEffect(() => {
    // The header survives scenario switches (no remount): a stale response from
    // the previous scenario must not overwrite the current one. simpleCall has
    // already notified the user on failure, hence the deliberately empty catch.
    let stale = false;
    fetchScenarioExpectationsDrift(scenarioId)
      .then((result: { data: ExpectationsDriftOutput }) => {
        if (!stale) setExpectationsDrift(result.data);
      })
      .catch(() => {});
    return () => {
      stale = true;
    };
  }, [scenarioId, scenario, injectsCount]);

  const onRealignExpectations = async () => {
    await realignScenarioExpectations(scenarioId);
    const result = await fetchScenarioExpectationsDrift(scenarioId);
    setExpectationsDrift(result.data);
    dispatch(fetchScenarioInjectsSimple(scenarioId));
  };

  // Dismissal is persisted in database (shared between users); the endpoint
  // returns the refreshed drift report.
  const onDismissExpectations = async (dismissed: boolean) => {
    const result = await dismissScenarioExpectationsDrift(scenarioId, dismissed);
    setExpectationsDrift(result.data);
  };

  // The schedule chip / tooltip derive directly from the store: the dialog
  // owns its own form state.
  const cronObject = useMemo(() => handle(scenario.scenario_recurrence), [scenario.scenario_recurrence]);

  const onSubmit = (cron: Cron, start: string, end?: string) => {
    dispatch(updateScenarioRecurrence(scenarioId, {
      scenario_recurrence: cron.toCronExpression(),
      scenario_recurrence_start: start,
      scenario_recurrence_end: end,
    }));
    setOpenScheduling(false);
  };

  const stop = () => {
    dispatch(updateScenarioRecurrence(scenarioId, {
      scenario_recurrence: undefined,
      scenario_recurrence_start: undefined,
      scenario_recurrence_end: undefined,
    }));
  };

  const humanReadableScheduling = () => {
    if (!cronObject?.isValid()) {
      return null;
    }
    let sentence = `${cronObject.toTranslatableStringArray(locale).map(element => t(element)).join(' ')}`;
    if (scenario.scenario_recurrence_end) {
      sentence += ` ${t('recurrence_from')} ${fld(scenario.scenario_recurrence_start)}`;
      sentence += ` ${t('recurrence_to')} ${fld(scenario.scenario_recurrence_end)}`;
    } else {
      sentence += ` ${t('recurrence_starting_from')} ${fld(scenario.scenario_recurrence_start)}`;
    }
    return sentence;
  };

  const scheduleLabel = cronObject?.isValid() ? humanReadableScheduling() : t('Not scheduled');

  return (
    <>
      <Box sx={{ marginBottom: 2 }}>
        <DetailHero
          icon={RouteOutlined}
          title={truncate(scenario.scenario_name, 80) ?? ''}
          chips={(
            <>
              {/* Autonomous run status sits first (left), rendered with the SAME chip a simulation
                  shows for its ExerciseStatus (AutonomousRunStatusChip mirrors ExerciseStatus) - the
                  single source of truth for the run state, so it is not duplicated in the hero
                  actions or the reasoning panel. */}
              {isAutonomous && autonomousStatus && (
                <AutonomousRunStatusChip status={autonomousStatus} variant="list" />
              )}
              <ItemSeverity severity={scenario.scenario_severity} label={t(scenario.scenario_severity ?? 'Unknown')} />
              <ItemCategory category={scenario.scenario_category ?? 'Unknown'} label={t(scenario.scenario_category ?? 'Unknown')} size="small" />
              <Tooltip title={scheduleLabel ?? ''}>
                <Chip
                  size="small"
                  variant="outlined"
                  label={isScheduled ? t('Scheduled') : t('Not scheduled')}
                  sx={{
                    borderRadius: 1,
                    height: 22,
                    fontSize: 11,
                    color: isScheduled ? theme.palette.success.main : theme.palette.text.disabled,
                    borderColor: isScheduled ? alpha(theme.palette.success.main, 0.4) : theme.palette.divider,
                  }}
                />
              </Tooltip>
            </>
          )}
          action={(
            <>
              {/* Contextual configuration alert - self-hides when healthy. Autonomous runs are
                  scoped and driven by the AI, so the "configure scope" nudge never applies. */}
              {canManage && !isAutonomous && (
                <HealthcheckIndicator healthchecks={healthchecks} scenarioId={scenarioId} />
              )}
              {/* Expectation drift warning - self-hides when aligned or dismissed. */}
              {canManage && !isAutonomous && (
                <ExpectationsDriftIndicator
                  drift={expectationsDrift}
                  variant="scenario"
                  onRealign={onRealignExpectations}
                  onDismiss={onDismissExpectations}
                  placement="warning"
                />
              )}
              {/* Configuration promoted to a first-class button (not buried in the
                  overflow) so teams/players setup is discoverable, with an
                  explicit tooltip describing what it configures. */}
              {canManage && !isScenarioChaining && (
                <Tooltip title={t('Configure the teams, players and audience targeted by this scenario')}>
                  <Button
                    variant="outlined"
                    color="primary"
                    size="small"
                    startIcon={<TuneOutlined />}
                    onClick={() => setOpenConfiguration(true)}
                    data-testid="scenario-configuration-button"
                  >
                    {t('Configuration')}
                  </Button>
                </Tooltip>
              )}
              {/* Dismissed drift downgraded to a discreet icon after Configuration -
                  the drift is acknowledged but still reviewable. */}
              {canManage && !isAutonomous && (
                <ExpectationsDriftIndicator
                  drift={expectationsDrift}
                  variant="scenario"
                  onRealign={onRealignExpectations}
                  onDismiss={onDismissExpectations}
                  placement="dismissed"
                />
              )}
              {/* Secondary actions surfaced as compact icon buttons (with explicit
                  tooltips) instead of being buried in the overflow menu. */}
              {/* Visible as soon as one inject uses a challenge - opens the
                  player-facing challenges page in a new tab. */}
              {hasChallenges && (
                <Tooltip title={t('Preview challenges page')}>
                  <IconButton
                    size="small"
                    color="primary"
                    component={Link}
                    to={`/admin/scenarios/${scenarioId}/challenges`}
                    target="_blank"
                  >
                    <EmojiEventsOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
              {/* Entity-scoped reports - self-hides without the reporting
                  access capability. */}
              <EntityReportsPanel
                contextType="SCENARIO"
                contextId={scenarioId}
                entityName={scenario.scenario_name}
              />
              {/* Scheduling stays available for autonomous scenarios too - an autonomous run can be
                  scheduled to recur exactly like any other scenario. Only the hand-authoring
                  assistant is withheld (the AI owns the attack-path logic). */}
              {canManage && (
                <>
                  <TriggerSubscribeButton
                    resourceType="SCENARIO"
                    resourceId={scenarioId}
                    resourceName={scenario.scenario_name}
                  />
                  <Tooltip title={t('Scheduling')}>
                    <IconButton size="small" color="primary" onClick={() => setOpenScheduling(true)}>
                      <UpdateOutlined fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  {/* Guided scenario building (matrix coverage + inject generation),
                      not an AI-only feature - hence primary color, no sparkles. */}
                  {!isScenarioChaining && (
                    <Tooltip title={t('Scenario assistant')}>
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => navigate(`/admin/scenarios/${scenarioId}/assistant`)}
                        data-testid="scenario-assistant-button"
                      >
                        <DashboardCustomizeOutlined fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                </>
              )}
              {/* Autonomous lifecycle: pause / resume / stop the run and its single simulation,
                  without leaving the scenario and without ever exposing a manual launch. */}
              {autonomousRun && (
                <AutonomousRunControls run={autonomousRun} onRunUpdate={onAutonomousRunUpdate} />
              )}
              {/* The single prominent CTA - never a manual launch on an autonomous run. */}
              {!isAutonomous && (canLaunch && isScheduled && !ended
                ? (
                    <>
                      <Button
                        startIcon={<Stop />}
                        variant="outlined"
                        color="inherit"
                        size="small"
                        onClick={stop}
                      >
                        {t('Stop')}
                      </Button>
                      {/* Even while scheduled, allow a one-off manual run outside
                          the recurrence - compact icon so it stays secondary to Stop. */}
                      <Tooltip title={isScopeMissing ? t('A chained scenario requires a defined scope.') : t('Launch now')}>
                        <span style={{ display: 'inline-flex' }}>
                          <IconButton
                            size="small"
                            color="primary"
                            onClick={() => setOpenInstantiateSimulationAndStart(true)}
                            disabled={isScopeMissing}
                            data-testid="scenario-launch-now-button"
                          >
                            <PlayArrowOutlined fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </>
                  )
                : canLaunch && (
                  <Tooltip title={isScopeMissing ? t('A chained scenario requires a defined scope.') : ''}>
                    <span style={{ display: 'inline-flex' }}>
                      <Button
                        startIcon={<PlayArrowOutlined />}
                        variant="contained"
                        color="primary"
                        size="small"
                        onClick={() => setOpenInstantiateSimulationAndStart(true)}
                        disabled={isScopeMissing}
                      >
                        {t('Launch now')}
                      </Button>
                    </span>
                  </Tooltip>
                ))}
              {/* Everything else - analyze, setup, and CRUD - in one overflow menu. Deleting an
                  autonomous scenario tears down its single simulation and stops the run. */}
              <ScenarioPopover
                scenario={scenario}
                actions={scenarioPopoverActions}
                onDelete={() => navigate('/admin/scenarios')}
                deleteDisabled={isAutonomousActive}
                deleteDisabledMessage={isAutonomousActive
                  ? t('Stop the autonomous run before deleting its scenario.')
                  : undefined}
              />
            </>
          )}
          stats={(
            <>
              {/* Always-on core stats. */}
              <HeroStat
                icon={TrackChangesOutlined}
                label={t('Injects')}
                value={injectsCount}
                color={theme.palette.warning.main}
                to={`/admin/scenarios/${scenarioId}/injects`}
              />
              <HeroStat
                icon={HubOutlined}
                label={t('Simulations')}
                value={simulationsCount}
                color={theme.palette.primary.main}
                to={simulationsCount > 0 ? statResultsUrl('simulation') : undefined}
              />
              {/* People dimension - tabletop / crisis scenarios. */}
              {teamsCount > 0 && (
                <HeroStat
                  icon={GroupsOutlined}
                  label={t('Teams')}
                  value={teamsCount}
                  color={theme.palette.secondary.main}
                  to={statResultsUrl('team', { base_id: teams.map(team => team.team_id) })}
                />
              )}
              {playersCount > 0 && (
                <HeroStat
                  icon={PersonOutlined}
                  label={t('Players')}
                  value={playersCount}
                  color={theme.palette.success.main}
                />
              )}
              {/* Technical dimension - endpoint-targeting scenarios. */}
              {assetsCount > 0 && (
                <HeroStat
                  icon={ComputerOutlined}
                  label={t('Assets')}
                  value={assetsCount}
                  color={theme.palette.info.main}
                  to={statResultsUrl('asset')}
                />
              )}
              {assetGroupsCount > 0 && (
                <HeroStat
                  icon={LanOutlined}
                  label={t('Asset groups')}
                  value={assetGroupsCount}
                  color={theme.palette.info.main}
                  to={statResultsUrl('asset-group', { base_id: assetGroupIds })}
                />
              )}
              {/* Content dimension - media pressure and gamification. */}
              {articlesCount > 0 && (
                <HeroStat
                  icon={NewspaperOutlined}
                  label={t('Media pressure')}
                  value={articlesCount}
                />
              )}
              {hasChallenges && (
                <HeroStat
                  icon={EmojiEventsOutlined}
                  label={t('Challenges')}
                  value={challenges.length}
                />
              )}
            </>
          )}
        />
      </Box>

      <SchedulingDialog
        open={openScheduling}
        onClose={() => setOpenScheduling(false)}
        initialValues={{
          recurrence: scenario.scenario_recurrence,
          recurrenceStart: scenario.scenario_recurrence_start,
          recurrenceEnd: scenario.scenario_recurrence_end,
        }}
        onSubmit={onSubmit}
      />
      <Dialog
        open={openInstantiateSimulationAndStart}
        TransitionComponent={Transition}
        onClose={() => setOpenInstantiateSimulationAndStart(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('A simulation will be launched based on this scenario and will start immediately. Are you sure you want to proceed?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={() => setOpenInstantiateSimulationAndStart(false)}>
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={async () => {
              setOpenInstantiateSimulationAndStart(false);
              const exercise: Exercise = (await createRunningExerciseFromScenario(scenarioId)).data;
              // A chained simulation is best followed live on its attack path
              // graph; time-based ones land on the overview as before. The
              // route only exists when ATTACK_PATH is enabled (see
              // simulation/Index.tsx route gating).
              if (isScenarioChaining && isAttackPathEnabled) {
                navigate(`${SIMULATION_BASE_URL}/${exercise.exercise_id}/attack-path`);
              } else {
                navigate(`${SIMULATION_BASE_URL}/${exercise.exercise_id}`);
              }
              MESSAGING$.notifySuccess(t('New simulation successfully created and started'));
            }}
          >
            {t('Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openConfiguration}
        handleClose={() => setOpenConfiguration(false)}
        title={t('Scenario configuration')}
      >
        <ScenarioConfiguration />
      </Drawer>
    </>
  );
};

export default ScenarioHeader;
