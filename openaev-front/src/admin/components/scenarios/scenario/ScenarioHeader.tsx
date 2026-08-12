import {
  AccountTreeOutlined,
  AutoAwesome,
  AutoFixHigh,
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
import { type Dispatch, type ReactNode, type SetStateAction, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';

import { fetchScenarioAutonomousConfig, launchAutonomousFromScenario, planAutonomousScenario, saveScenarioAutonomousConfig } from '../../../../actions/autonomous/autonomous-actions';
import { type AutonomousRun, type AutonomousRunCreateInput } from '../../../../actions/autonomous/autonomous-types';
import { fetchSteps } from '../../../../actions/chaining/chaining-actions';
import type { WorkflowConfigurationHelper } from '../../../../actions/chaining/workflow-helper';
import { fetchScenarioChallenges } from '../../../../actions/challenge-action';
import { fetchScenarioArticles } from '../../../../actions/channels/article-action';
import { type ArticlesHelper } from '../../../../actions/channels/article-helper';
import { type ChallengeHelper } from '../../../../actions/helper';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import {
  createRunningExerciseFromScenario,
  dismissScenarioExpectationsDrift,
  fetchScenario,
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
import useAuth from '../../../../utils/hooks/useAuth';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { type Cron } from '../../../../utils/period/Cron';
import handle from '../../../../utils/period/Period';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { truncate } from '../../../../utils/String';
import { isFeatureEnabled } from '../../../../utils/utils';
import isXtmOneAvailable from '../../ariane/xtmOneAvailability';
import AutonomousRunConfigDrawer from '../../autonomous/AutonomousRunConfigDrawer';
import AutonomousRunControls from '../../autonomous/AutonomousRunControls';
import AutonomousRunStatusChip from '../../autonomous/AutonomousRunStatusChip';
import { isAutonomousRunActive, isAutonomousRunSettled } from '../../autonomous/autonomousStatus';
import { DEFAULT_TIMEOUT_HOURS } from '../../autonomous/useAutonomousRunConfig';
import EEChip from '../../common/entreprise_edition/EEChip';
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
  /** The autonomous run owning this scenario (plan-mode design session or a live autonomous launch),
   *  or null. Drives the hero's status chip + lifecycle controls, and gates the launch actions. */
  autonomousRun?: AutonomousRun | null;
  /** Push a fresher run up (a just-started plan/launch, or a status transition from the controls) so
   *  the whole scenario page reveals / refreshes the AI cockpit without a second poll loop. */
  onAutonomousRunUpdate?: (run: AutonomousRun) => void;
  /** Forget the detected run so the overview reverts to the manual view immediately: a normal launch
   *  supersedes a settled AI outcome server-side, so the stale plan/run outcome must stop rendering
   *  without waiting for a full page reload. */
  onAutonomousRunCleared?: () => void;
}

const ScenarioHeader = ({
  openInstantiateSimulationAndStart,
  setOpenInstantiateSimulationAndStart,
  autonomousRun = null,
  onAutonomousRunUpdate,
  onAutonomousRunCleared,
}: ScenarioHeaderProps) => {
  // Standard hooks
  const { t, locale, fld } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const [openScenarioAssistantQueryParam, openAiBuilderQueryParam, openAiLaunchQueryParam] = useQueryParameter(['openScenarioAssistant', 'openAiBuilder', 'openAiLaunch']);
  const { canLaunch, canManage, canDelete } = useScenarioPermissions(scenarioId);
  const { settings } = useAuth();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const [openConfiguration, setOpenConfiguration] = useState(false);
  const [openScheduling, setOpenScheduling] = useState(false);
  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);
  const [expectationsDrift, setExpectationsDrift] = useState<ExpectationsDriftOutput | null>(null);
  // Number of authored attack-path steps on the scenario's chaining workflow. A chained scenario
  // builds its attack path as workflow step templates, not classic scenario injects, so this is the
  // meaningful "how big is the attack path" stat for the hero.
  const [attackPathStepCount, setAttackPathStepCount] = useState<number | null>(null);
  // The shared AI-run configuration drawer (objective, agents, scope, time budget), plus which
  // action it is scoped to: "build" (the AI builder - Save the config for later or Build/plan it
  // now, authoring onto the scenario) or "launch" (Autonomous - live run). Also its in-flight /
  // error state and the saved config it pre-fills from.
  const [aiDrawerOpen, setAiDrawerOpen] = useState(false);
  const [aiDrawerIntent, setAiDrawerIntent] = useState<'build' | 'launch'>('build');
  const [aiInitialInput, setAiInitialInput] = useState<AutonomousRunCreateInput | null>(null);
  const [aiSubmitting, setAiSubmitting] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);
  // Fetch the scenario's saved AI config BEFORE opening, so the drawer's hook seeds from it on the
  // open toggle (its agent effect reads initialInput off the first open). data is null when nothing
  // was saved yet, which the hook treats as "use tenant defaults".
  const openAiDrawer = async (intent: 'build' | 'launch') => {
    setAiError(null);
    let saved: AutonomousRunCreateInput | null = null;
    try {
      saved = (await fetchScenarioAutonomousConfig(scenarioId)).data;
    } catch {
      saved = null;
    }
    // A live autonomous launch always re-proposes the 24h default budget: drop only the saved
    // timeout (a legacy builder config may carry the former 1h plan budget) so the objective,
    // agents and scope stay prefilled while the advertised 24h default is actually applied.
    setAiInitialInput(intent === 'launch' && saved
      ? {
          ...saved,
          timeout_seconds: undefined,
        }
      : saved);
    setAiDrawerIntent(intent);
    setAiDrawerOpen(true);
  };

  // EE-aware entry point for both AI actions (Autonomous launch + AI builder): on a non-Enterprise
  // platform they degrade to the standard EE call-to-action (the same dialog + feature label the
  // creation drawer raises) instead of opening the config drawer. XTM One availability is enforced
  // at render (the buttons are not shown without it), so this only arbitrates the EE gate.
  const openAiDrawerOrEE = (intent: 'build' | 'launch') => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Autonomous attack path'));
      openEnterpriseEditionDialog();
      return;
    }
    void openAiDrawer(intent);
  };

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
  // Autonomy is a launch-time MODE now (not a scenario type) and no longer has a dedicated flag: any
  // chained scenario can be launched autonomously (orchestrator-driven) or planned by the
  // orchestrator, gated by the same chaining feature. Time-based scenarios only ever launch a normal
  // simulation.
  const isAutonomousModeEnabled = isScenarioChaining;
  // The Autonomous launch + AI builder are XTM One-driven Enterprise features, so they are gated
  // exactly like the top-bar AI shortcuts (hidden unless XTM One is connected, via the shared
  // isXtmOneAvailable predicate) AND like the scenario creation drawer (a standard EE call-to-action
  // when the platform is not Enterprise). Previously they were gated only on chaining, so they showed
  // even with no XTM One available and led nowhere.
  const isXtmOneReady = isXtmOneAvailable(settings);
  // A run is "active" while the orchestrator is planning or driving: the hero then shows lifecycle
  // controls (pause / resume / stop) instead of the launch actions, and the page hosts the cockpit.
  // A settled run (PLANNED / completed) leaves the launch actions available again so the operator can
  // relaunch normally or in autonomous mode, or promote / redo the plan from the controls.
  const isRunActive = isAutonomousRunActive(autonomousRun);
  // A settled run (PLANNED / completed / failed / canceled) leaves an authored attack path (and,
  // for a live run, results) behind: the hero CTAs then read as Rebuild (AI) / Relaunch rather than
  // the first-time Build / Launch, and Build wipes the logic map to re-plan from scratch.
  const isRunSettled = isAutonomousRunSettled(autonomousRun);

  // Deep link from scenario creation ("Generate with AI" toggle): auto-open the AI builder drawer
  // once, then strip the query param so a refresh / back does not reopen it. Only for a chained
  // scenario the operator can manage and while no run already owns it.
  useEffect(() => {
    if (openAiBuilderQueryParam === 'true' && canManage && isAutonomousModeEnabled && !isRunActive && isXtmOneReady && isEnterpriseEdition) {
      void openAiDrawer('build');
      navigate(location.pathname, { replace: true });
    }
  }, [openAiBuilderQueryParam, canManage, isAutonomousModeEnabled, isRunActive, isXtmOneReady, isEnterpriseEdition, scenarioId]);

  // Deep link from the overview "no run yet" banner (Autonomous button): open the launch config
  // drawer so the operator configures the objective / agents / scope, then launches the live run.
  // The header owns the drawer (single control surface), so the banner just routes here. Strip the
  // param after so a refresh / back does not reopen it.
  useEffect(() => {
    if (openAiLaunchQueryParam === 'true' && canManage && isAutonomousModeEnabled && !isRunActive && isXtmOneReady && isEnterpriseEdition) {
      void openAiDrawer('launch');
      navigate(location.pathname, { replace: true });
    }
  }, [openAiLaunchQueryParam, canManage, isAutonomousModeEnabled, isRunActive, isXtmOneReady, isEnterpriseEdition, scenarioId]);

  const { workflowConfiguration } = useHelper((helper: WorkflowConfigurationHelper) => ({
    workflowConfiguration: scenarioWorkflowId
      ? helper.getWorkflowConfiguration(scenarioWorkflowId)
      : undefined,
  }));
  // A chained scenario keeps its attack-path logic (Update / Delete / Export); a time-based one may
  // also be duplicated by hand.
  const scenarioPopoverActions: ('Duplicate' | 'Update' | 'Delete' | 'Export')[] = isScenarioChaining
    ? ['Update', 'Delete', 'Export']
    : ['Duplicate', 'Update', 'Delete', 'Export'];
  // Grant-only users without any of the manage / launch / delete permissions get no overflow menu
  // at all instead of a popover full of disabled entries.
  const canDisplayScenarioActions = canManage || canLaunch || canDelete;
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

  // Count the attack-path steps that back the hero stat, read from the scenario's chaining workflow
  // (its authored step templates).
  useEffect(() => {
    let stale = false;
    if (scenarioWorkflowId) {
      fetchSteps(scenarioWorkflowId)
        .then((result) => {
          if (!stale) setAttackPathStepCount(result.data?.length ?? 0);
        })
        .catch(() => {});
    } else {
      setAttackPathStepCount(null);
    }
    return () => {
      stale = true;
    };
  }, [scenarioWorkflowId]);

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

  // Normal launch: a plain, operator-driven simulation from the scenario. Opens the confirm dialog.
  const handleLaunchNormal = () => setOpenInstantiateSimulationAndStart(true);

  // AI builder - Save: persist the configuration on the scenario WITHOUT starting anything. The
  // scenario stays a normal, editable chained scenario; the operator can build or launch it later
  // and the drawer will pre-fill from this. Launch stays a separate second step.
  const handleAiSave = async (input: AutonomousRunCreateInput) => {
    setAiSubmitting(true);
    setAiError(null);
    try {
      await saveScenarioAutonomousConfig(scenarioId, input);
      setAiDrawerOpen(false);
      MESSAGING$.notifySuccess(t('AI configuration saved. You can build or launch it later.'));
    } catch {
      setAiError(t('Failed to save the AI configuration'));
    } finally {
      setAiSubmitting(false);
    }
  };

  // AI builder - Build: the orchestrator designs the attack path by writing steps onto this
  // scenario's workflow from the configured objective / agents / scope. Nothing is executed; the
  // operator launches it later. The config is persisted first so it survives (re-buildable if the
  // plan is discarded). Push the run up so the planning cockpit appears, and land on Logic to watch
  // the authored workflow take shape.
  const handleAiBuild = async (input: AutonomousRunCreateInput) => {
    setAiSubmitting(true);
    setAiError(null);
    try {
      await saveScenarioAutonomousConfig(scenarioId, input);
      const { data } = await planAutonomousScenario(scenarioId, input);
      onAutonomousRunUpdate?.(data);
      setAiDrawerOpen(false);
      MESSAGING$.notifySuccess(t('The orchestrator is building the logic for this scenario'));
      navigate(`/admin/scenarios/${scenarioId}/logic`);
    } catch {
      setAiError(t('Failed to start the build'));
    } finally {
      setAiSubmitting(false);
    }
  };

  // Autonomous launch: seed a live simulation from the scenario's authored attack path and the
  // configured objective / agents / scope, then engage the orchestrator. The scenario page IS the
  // control surface (single reasoning panel + lifecycle controls) and stays synced with the live
  // simulation through the Execution / Attack path tabs - so we push the run up to reveal the
  // cockpit and stay here rather than jumping to the simulation (whose cockpit is a read-only
  // mirror). Landing on the scenario overview puts the operator in front of the AI cockpit -
  // timeline, gaps and findings - while the Attack path tab stays one click away.
  const handleAiLaunch = async (input: AutonomousRunCreateInput) => {
    setAiSubmitting(true);
    setAiError(null);
    try {
      const { data } = await launchAutonomousFromScenario(scenarioId, input);
      onAutonomousRunUpdate?.(data);
      // Refresh the scenario so its exercise list (Execution / Attack path tabs) picks up the live
      // simulation the launch just provisioned - keeping the scenario view in sync with the run.
      dispatch(fetchScenario(scenarioId));
      setAiDrawerOpen(false);
      MESSAGING$.notifySuccess(t('Autonomous run launched; the orchestrator is now driving the simulation'));
      navigate(`/admin/scenarios/${scenarioId}`);
    } catch {
      setAiError(t('Failed to launch the autonomous run'));
    } finally {
      setAiSubmitting(false);
    }
  };

  // Resolved out of the JSX to avoid nested ternaries: the Normal launch tooltip depends on scope
  // validity first, then on whether this is a first launch or a relaunch of a settled run.
  let normalLaunchTitle: string;
  if (isScopeMissing) {
    normalLaunchTitle = t('A chained scenario requires a defined scope.');
  } else if (isRunSettled) {
    normalLaunchTitle = t('Relaunch this scenario in normal mode - runs only the predefined steps, no live AI adaptation');
  } else {
    normalLaunchTitle = t('Launch this scenario in normal mode - runs only the predefined steps, no live AI adaptation');
  }

  // AI config drawer title + primary action label: "launch" intent is always a live Autonomous
  // launch; "build" reads as Rebuild once a settled run left an authored attack path behind.
  let aiDrawerTitle: string;
  if (aiDrawerIntent !== 'build') {
    aiDrawerTitle = t('Launch in autonomous mode');
  } else if (isRunSettled) {
    aiDrawerTitle = t('Rebuild the attack path');
  } else {
    aiDrawerTitle = t('AI builder');
  }
  let aiLaunchLabel: string;
  if (aiDrawerIntent !== 'build') {
    aiLaunchLabel = t('Launch now');
  } else if (isRunSettled) {
    aiLaunchLabel = t('Rebuild');
  } else {
    aiLaunchLabel = t('Build');
  }

  // Autonomous-launch objective UX depends on how defined the scenario already is:
  //   1. Manually authored (has attack-path steps, no saved AI config) - and
  //   2. AI-built (a saved AI config with an objective)
  // both read as "execute what is already defined, then iterate": the launch drawer leads with a
  // pre-seeded free-text mission (the saved objective when present, else a default execute-first
  // mission) and demotes the objective-template gallery into a collapsed accordion, signalling that
  // picking a template is an override rather than the normal course.
  //   3. Nothing defined yet (no steps, no saved config) - keep the normal template-first drawer so
  // the operator defines the objective from scratch.
  // Only the live-launch intent gets this; the AI builder (design/author) keeps templates upfront.
  const launchHasDefinition = (attackPathStepCount ?? 0) > 0 || !!aiInitialInput;
  const aiDemoteTemplates = aiDrawerIntent === 'launch' && launchHasDefinition;
  const aiDefaultObjective = aiDemoteTemplates
    ? t('Execute the attack path already defined in this scenario first, then continue autonomously: adapt to live findings, progress toward the objective, and expand within the authorized scope.')
    : undefined;

  // Autonomous launch always defaults to a 24h run budget (recon + human-in-the-loop steps make a
  // live run long-lived), overriding the scenario's own "Simulation time out" whatever it is. Warn
  // the operator with a tiny note, but ONLY when the scenario's configured timeout actually differs
  // from 24h - if they already set exactly 24h, the override is invisible so no note is needed.
  // Compared in raw seconds (the scope editor supports minutes, e.g. 23h30) so a near-24h config
  // never rounds into a false match. A disabled/unset timeout also differs from 24h, so it warns.
  const scenarioTimeoutSeconds = workflowConfiguration?.workflow_configuration_timeout_enabled
    && workflowConfiguration.workflow_configuration_timeout_seconds
    ? workflowConfiguration.workflow_configuration_timeout_seconds
    : null;
  const aiTimeBudgetNote = aiDrawerIntent === 'launch' && scenarioTimeoutSeconds !== DEFAULT_TIMEOUT_HOURS * 3600
    ? t('Autonomous runs default to a 24h budget for the best experience, overriding this scenario\'s configured timeout. You can still reduce it below.')
    : undefined;

  // Launch actions for the hero, resolved here (rather than as nested ternaries in the JSX). A
  // scheduled scenario keeps the Stop + one-off Launch-now controls; a chained autonomous-capable
  // scenario shows two explicit buttons - Normal (a plain operator-driven simulation) and Autonomous
  // (AI-purple, opens the config drawer and drives it live); everything else launches a normal
  // simulation directly. Rendered only while no run is active and the operator may launch.
  let launchActions: ReactNode;
  if (isScheduled && !ended) {
    launchActions = (
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
          <Box component="span" sx={{ display: 'inline-flex' }}>
            <IconButton
              size="small"
              color="primary"
              onClick={handleLaunchNormal}
              disabled={isScopeMissing}
              data-testid="scenario-launch-now-button"
            >
              <PlayArrowOutlined fontSize="small" />
            </IconButton>
          </Box>
        </Tooltip>
      </>
    );
  } else if (isAutonomousModeEnabled) {
    launchActions = (
      <>
        <Tooltip title={normalLaunchTitle}>
          <Box component="span" sx={{ display: 'inline-flex' }}>
            <Button
              startIcon={<PlayArrowOutlined />}
              variant="contained"
              color="primary"
              size="small"
              onClick={handleLaunchNormal}
              disabled={isScopeMissing}
              data-testid="scenario-launch-button"
            >
              {t('Normal')}
            </Button>
          </Box>
        </Tooltip>
        {/* Autonomous is an XTM One-driven EE feature: hidden entirely when XTM One is unavailable
            (only Normal remains), and shown as an EE call-to-action when the platform is not
            Enterprise (the EE chip + the dialog raised by openAiDrawerOrEE). */}
        {isXtmOneReady && (
          <Tooltip title={isRunSettled
            ? t('Relaunch in autonomous mode - configure the objective, agents and scope, then let the orchestrator drive and adapt from live findings')
            : t('Launch in autonomous mode - configure the objective, agents and scope, then let the orchestrator drive and adapt from live findings')}
          >
            <Box
              component="span"
              sx={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 0.5,
              }}
            >
              <Button
                startIcon={<AutoAwesome />}
                variant="contained"
                size="small"
                onClick={() => openAiDrawerOrEE('launch')}
                data-testid="scenario-launch-autonomous-button"
                sx={{
                  'whiteSpace': 'nowrap',
                  'backgroundColor': theme.palette.ai.main,
                  'color': theme.palette.ai.contrastText,
                  '&:hover': { backgroundColor: theme.palette.ai.dark },
                }}
              >
                {t('Autonomous')}
              </Button>
              {!isEnterpriseEdition && <EEChip />}
            </Box>
          </Tooltip>
        )}
      </>
    );
  } else {
    launchActions = (
      <Tooltip title={isScopeMissing ? t('A chained scenario requires a defined scope.') : ''}>
        <Box component="span" sx={{ display: 'inline-flex' }}>
          <Button
            startIcon={<PlayArrowOutlined />}
            variant="contained"
            color="primary"
            size="small"
            onClick={handleLaunchNormal}
            disabled={isScopeMissing}
            data-testid="scenario-launch-button"
          >
            {t('Launch')}
          </Button>
        </Box>
      </Tooltip>
    );
  }

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
              <ItemSeverity severity={scenario.scenario_severity} label={t(scenario.scenario_severity ?? 'Unknown')} />
              <ItemCategory category={scenario.scenario_category ?? 'Unknown'} label={t(scenario.scenario_category ?? 'Unknown')} size="small" />
              {/* While a run owns the scenario, surface its status right next to severity/category -
                  the same chip a simulation shows - so the AI lifecycle reads at a glance. */}
              {autonomousRun && (
                <AutonomousRunStatusChip status={autonomousRun.autonomous_run_status} />
              )}
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
              {/* Contextual configuration alert - self-hides when healthy. */}
              {canManage && (
                <HealthcheckIndicator healthchecks={healthchecks} scenarioId={scenarioId} />
              )}
              {/* Expectation drift warning - self-hides when aligned or dismissed. */}
              {canManage && (
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
              {canManage && (
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
              {/* While a run is ACTIVE it owns the hero: the launch actions are swapped for the
                  run's lifecycle controls (pause / resume / stop) - the same single control
                  surface the simulation defers to. Once the run settles (planned / completed /
                  failed / canceled) the standard actions return: rebuild through the AI builder
                  (Build wipes the logic map and re-plans) or relaunch Normal / Autonomous. */}
              {isRunActive && autonomousRun && (
                <AutonomousRunControls run={autonomousRun} onRunUpdate={onAutonomousRunUpdate} />
              )}
              {/* AI builder: compact AI-purple icon button. Opens the shared config drawer scoped to
                  the "build" action - the operator configures the objective / agents / scope, then
                  Saves it for later or Builds it now (the orchestrator authors the attack path onto
                  this scenario, nothing executed). Hidden while a run is active (its lifecycle
                  controls own the hero then). */}
              {/* AI builder: an XTM One-driven EE feature, gated like the Autonomous button - hidden
                  unless XTM One is available, and an EE call-to-action (EE chip + EE dialog via
                  openAiDrawerOrEE) when the platform is not Enterprise. */}
              {canManage && isAutonomousModeEnabled && !isRunActive && isXtmOneReady && (
                <Tooltip title={isRunSettled
                  ? t('Rebuild with AI - re-author this scenario\'s logic (this wipes the current logic map and starts fresh)')
                  : t('AI builder - let the orchestrator author this scenario\'s logic; save it for later or build it now (nothing runs while building)')}
                >
                  <Box
                    component="span"
                    sx={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 0.5,
                    }}
                  >
                    <IconButton
                      size="small"
                      onClick={() => openAiDrawerOrEE('build')}
                      aria-label={isRunSettled ? t('Rebuild with AI') : t('AI builder')}
                      data-testid="scenario-plan-with-ai-button"
                      sx={{
                        'color': theme.palette.ai.main,
                        '&:hover': {
                          color: theme.palette.ai.dark,
                          backgroundColor: alpha(theme.palette.ai.main, 0.08),
                        },
                      }}
                    >
                      <AutoFixHigh fontSize="small" />
                    </IconButton>
                    {!isEnterpriseEdition && <EEChip />}
                  </Box>
                </Tooltip>
              )}
              {/* Launch actions (suppressed while a run is active - the lifecycle controls own the
                  hero then). Resolved into `launchActions` above to avoid nested ternaries here. */}
              {!isRunActive && canLaunch && launchActions}
              {/* Everything else - analyze, setup, and CRUD - in one overflow menu. Hidden entirely
                  for grant-only users without any manage / launch / delete permission. */}
              {canDisplayScenarioActions && (
                <ScenarioPopover
                  scenario={scenario}
                  actions={scenarioPopoverActions}
                  onDelete={() => navigate('/admin/scenarios')}
                />
              )}
            </>
          )}
          stats={(
            <>
              {/* Always-on core stats. A workflow-backed (chained) scenario builds its attack path
                  as workflow step templates rather than classic scenario injects, so it surfaces an
                  "Attack path steps" stat. A time-based scenario keeps the plain Injects stat
                  pointing at its authoring tab. */}
              {isScenarioChaining
                ? (
                    <HeroStat
                      icon={AccountTreeOutlined}
                      label={t('Attack path steps')}
                      value={attackPathStepCount ?? 0}
                      color={theme.palette.warning.main}
                      to={isAttackPathEnabled
                        ? `/admin/scenarios/${scenarioId}/attack-path`
                        : `/admin/scenarios/${scenarioId}/execution`}
                    />
                  )
                : (
                    <HeroStat
                      icon={TrackChangesOutlined}
                      label={t('Injects')}
                      value={injectsCount}
                      color={theme.palette.warning.main}
                      to={`/admin/scenarios/${scenarioId}/injects`}
                    />
                  )}
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
              // A normal launch supersedes any settled AI outcome server-side (the by-scenario run
              // lookup now 404s), so forget the latched run: the overview reverts to the manual view
              // instead of keeping the stale AI plan outcome + status chip until a full page reload.
              onAutonomousRunCleared?.();
              // A manual launch jumps into the simulation that was just created: a chained scenario
              // lands on the simulation's Attack path tab (the live execution view), a time-based
              // scenario on the simulation overview. Only the AUTONOMOUS launch stays on the
              // scenario (its attack-path tab hosts the AI cockpit) - see handleAiLaunch.
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
      {/* Shared AI-run configuration drawer, scoped to the action that opened it. "build" (AI
          builder) offers Save (persist the config, nothing runs) + Build (plan now, author the
          steps onto the scenario); "launch" (Autonomous) seeds a live simulation and drives it.
          Both pre-fill from the scenario's saved config and reuse the exact same objective / agents
          / scope config as the entity-level Autonomous attack action. Launch is always a second
          step - it is never offered from the builder. */}
      <AutonomousRunConfigDrawer
        open={aiDrawerOpen}
        onClose={() => setAiDrawerOpen(false)}
        initialInput={aiInitialInput}
        defaultObjective={aiDefaultObjective}
        demoteTemplates={aiDemoteTemplates}
        // Planning (the AI builder) is a quick, server-side-untimed design pass: plan mode hides the
        // time-budget field entirely and never sends a timeout, so this 1h default is only a belt (a
        // safety net if the field is ever shown). A live autonomous launch keeps the 24h default
        // (recon + human-in-the-loop steps make it long-lived).
        defaultTimeoutHours={aiDrawerIntent === 'build' ? 1 : undefined}
        planMode={aiDrawerIntent === 'build'}
        timeBudgetNote={aiTimeBudgetNote}
        title={aiDrawerTitle}
        infoText={aiDrawerIntent === 'build'
          ? t('Let the AI build this scenario\'s logic for you - set the objective, the specialist agents the orchestrator may consult, and the scope. Save it to build or launch later, or Build now to have the orchestrator author the steps onto the scenario. Nothing runs while building; you launch the scenario afterwards, in normal or autonomous mode.')
          : t('Launch this scenario in autonomous mode: the orchestrator seeds a live run from the objective, agents and scope below, then drives it and adapts in real time - reacting to findings, adding steps and consulting agents to pursue the objective within scope. (Normal mode instead runs only the scenario\'s predefined steps.)')}
        submitting={aiSubmitting}
        error={aiError}
        showSave={aiDrawerIntent === 'build'}
        showLaunch
        saveLabel={t('Save')}
        launchLabel={aiLaunchLabel}
        onSave={aiDrawerIntent === 'build' ? handleAiSave : undefined}
        onLaunch={aiDrawerIntent === 'build' ? handleAiBuild : handleAiLaunch}
      />
    </>
  );
};

export default ScenarioHeader;
