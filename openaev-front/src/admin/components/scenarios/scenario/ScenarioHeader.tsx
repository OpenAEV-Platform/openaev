import {
  AutoAwesomeOutlined,
  ComputerOutlined,
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
import { type Dispatch, type SetStateAction, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { fetchScenarioChallenges } from '../../../../actions/challenge-action';
import { fetchScenarioArticles } from '../../../../actions/channels/article-action';
import { type ArticlesHelper } from '../../../../actions/channels/article-helper';
import { type ChallengeHelper } from '../../../../actions/helper';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import {
  createRunningExerciseFromScenario,
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
import { type PeriodExpressionHandler } from '../../../../utils/period/PeriodExpressionHandler';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { truncate } from '../../../../utils/String';
import { isFeatureEnabled } from '../../../../utils/utils';
import HealthcheckIndicator from '../../common/healthchecks/HealthcheckIndicator';
import ExpectationsDriftIndicator from '../../common/injects/expectations/ExpectationsDriftIndicator';
import { countDistinctInjectTargets } from '../../common/injects/utils';
import TriggerSubscribeButton from '../../profile/triggers/TriggerSubscribeButton';
import ScenarioConfiguration from './ScenarioConfiguration';
import ScenarioPopover from './ScenarioPopover';
import ScenarioRecurringFormDialog from './ScenarioRecurringFormDialog';

interface ScenarioHeaderProps {
  cronObject: PeriodExpressionHandler | null;
  setCronObject: Dispatch<SetStateAction<PeriodExpressionHandler | null>>;
  setSelectRecurring: Dispatch<SetStateAction<string>>;
  selectRecurring: string;
  setOpenScenarioRecurringFormDialog: Dispatch<SetStateAction<boolean>>;
  setOpenInstantiateSimulationAndStart: Dispatch<SetStateAction<boolean>>;
  openScenarioRecurringFormDialog: boolean;
  openInstantiateSimulationAndStart: boolean;
  noRepeat: boolean;
}

const ScenarioHeader = ({
  cronObject,
  setCronObject,
  setSelectRecurring,
  selectRecurring,
  noRepeat,
  openScenarioRecurringFormDialog,
  setOpenScenarioRecurringFormDialog,
  openInstantiateSimulationAndStart,
  setOpenInstantiateSimulationAndStart,
}: ScenarioHeaderProps) => {
  // Standard hooks
  const { t, locale, fld } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const [openScenarioAssistantQueryParam] = useQueryParameter(['openScenarioAssistant']);
  const { canLaunch, canManage } = useScenarioPermissions(scenarioId);

  const [openConfiguration, setOpenConfiguration] = useState(false);
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
  const scenarioWorkflowId = (scenario as unknown as Record<string, unknown>).scenario_workflow_id as string | undefined;
  const isScenarioChaining = isChainingFeatureEnabled && !!scenarioWorkflowId;
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
  const { assets: assetsCount, assetGroups: assetGroupsCount } = countDistinctInjectTargets(injects);

  useEffect(() => {
    searchScenarioHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [scenarioId, scenario]);

  // Expectation drift between the injector contract templates and the inject
  // content - recomputed when the scenario or its inject set changes.
  useEffect(() => {
    fetchScenarioExpectationsDrift(scenarioId).then((result: { data: ExpectationsDriftOutput }) => setExpectationsDrift(result.data));
  }, [scenarioId, scenario, injectsCount]);

  const onRealignExpectations = async () => {
    await realignScenarioExpectations(scenarioId);
    const result = await fetchScenarioExpectationsDrift(scenarioId);
    setExpectationsDrift(result.data);
    dispatch(fetchScenarioInjectsSimple(scenarioId));
  };

  const onSubmit = (cron: Cron, start: string, end?: string) => {
    dispatch(updateScenarioRecurrence(scenarioId, {
      scenario_recurrence: cron.toCronExpression(),
      scenario_recurrence_start: start,
      scenario_recurrence_end: end,
    })).then((result: { [x: string]: string }) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        setCronObject(cron);
      }
    });
    setOpenScenarioRecurringFormDialog(false);
  };

  useEffect(() => {
    if (scenario.scenario_recurrence != null) {
      const newCron = handle(scenario.scenario_recurrence);
      setCronObject(newCron);
      if (noRepeat) {
        setSelectRecurring('noRepeat');
      } else {
        setSelectRecurring(newCron?.getRecurrenceMagnitude() || 'daily');
      }
    } else {
      setCronObject(null);
    }
  }, [scenario.scenario_recurrence]);

  const stop = () => {
    setCronObject(null);
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
              {/* Contextual configuration alert - self-hides when healthy. */}
              {canManage && (
                <HealthcheckIndicator healthchecks={healthchecks} scenarioId={scenarioId} />
              )}
              {/* Expectation drift warning - self-hides when aligned. */}
              {canManage && (
                <ExpectationsDriftIndicator
                  drift={expectationsDrift}
                  variant="scenario"
                  onRealign={onRealignExpectations}
                />
              )}
              {/* One AI action, kept visible for discoverability. */}
              {canManage && !isScenarioChaining && (
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<AutoAwesomeOutlined />}
                  sx={{
                    'color': theme.palette.ai.main,
                    'borderColor': alpha(theme.palette.ai.main, 0.5),
                    'backgroundColor': alpha(theme.palette.ai.main, 0.06),
                    '&:hover': {
                      borderColor: theme.palette.ai.main,
                      backgroundColor: alpha(theme.palette.ai.main, 0.12),
                    },
                  }}
                  onClick={() => navigate(`/admin/scenarios/${scenarioId}/assistant`)}
                >
                  {t('Scenario assistant')}
                </Button>
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
              {canManage && (
                <>
                  <TriggerSubscribeButton
                    resourceType="SCENARIO"
                    resourceId={scenarioId}
                    resourceName={scenario.scenario_name}
                  />
                  <Tooltip title={t('Scheduling')}>
                    <IconButton size="small" color="primary" onClick={() => setOpenScenarioRecurringFormDialog(true)}>
                      <UpdateOutlined fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </>
              )}
              {/* The single prominent CTA. */}
              {canLaunch && isScheduled && !ended
                ? (
                    <Button
                      startIcon={<Stop />}
                      variant="outlined"
                      color="inherit"
                      size="small"
                      onClick={stop}
                    >
                      {t('Stop')}
                    </Button>
                  )
                : canLaunch && (
                  <Tooltip title={isScopeMissing ? t('A Chaining Scenario requires a defined scope.') : ''}>
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
                )}
              {/* Everything else - analyze, setup, and CRUD - in one overflow menu. */}
              <ScenarioPopover
                scenario={scenario}
                actions={isScenarioChaining ? ['Update', 'Delete', 'Export'] : ['Duplicate', 'Update', 'Delete', 'Export']}
                onDelete={() => navigate('/admin/scenarios')}
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
              />
              {/* People dimension - tabletop / crisis scenarios. */}
              {teamsCount > 0 && (
                <HeroStat
                  icon={GroupsOutlined}
                  label={t('Teams')}
                  value={teamsCount}
                  color={theme.palette.secondary.main}
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
                />
              )}
              {assetGroupsCount > 0 && (
                <HeroStat
                  icon={LanOutlined}
                  label={t('Asset groups')}
                  value={assetGroupsCount}
                  color={theme.palette.info.main}
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

      <ScenarioRecurringFormDialog
        cronObject={cronObject}
        setCronObject={setCronObject}
        selectRecurring={selectRecurring}
        onSelectRecurring={setSelectRecurring}
        open={openScenarioRecurringFormDialog}
        setOpen={setOpenScenarioRecurringFormDialog}
        onSubmit={onSubmit}
        initialValues={scenario}
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
              navigate(`${SIMULATION_BASE_URL}/${exercise.exercise_id}`);
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
