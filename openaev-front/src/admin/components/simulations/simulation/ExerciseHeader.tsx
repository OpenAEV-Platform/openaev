import {
  CancelOutlined,
  ComputerOutlined,
  EmojiEventsOutlined,
  GroupsOutlined,
  LanOutlined,
  NewspaperOutlined,
  PauseOutlined,
  PersonOutlined,
  PlayArrowOutlined,
  PlayCircleOutlineOutlined,
  RestartAltOutlined,
  TrackChangesOutlined,
  TuneOutlined,
  UpdateOutlined,
} from '@mui/icons-material';
import { Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../actions/chaining/workflow-helper';
import { fetchExerciseChallenges } from '../../../../actions/challenge-action';
import { fetchExerciseArticles } from '../../../../actions/channels/article-action';
import { type ArticlesHelper } from '../../../../actions/channels/article-helper';
import { dismissExerciseExpectationsDrift, fetchExerciseExpectationsDrift, fetchExerciseTeams, realignExerciseExpectations, searchExerciseHealthchecks, updateExerciseStatus } from '../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import { type ChallengeHelper } from '../../../../actions/helper';
import { fetchExerciseInjectsSimple } from '../../../../actions/Inject';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import { DetailHero, HeroStat } from '../../../../components/common/detail/EntityDetailCommon';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemSeverity from '../../../../components/ItemSeverity';
import { useHelper } from '../../../../store';
import { type Article, type Challenge, type Exercise, type Exercise as ExerciseType, type ExpectationsDriftOutput, type HealthCheck, type Inject, type SimulationDetails, type Team } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSimulationPermissions from '../../../../utils/permissions/useSimulationPermissions';
import { truncate } from '../../../../utils/String';
import { isFeatureEnabled } from '../../../../utils/utils';
import HealthcheckIndicator from '../../common/healthchecks/HealthcheckIndicator';
import { getScopeAwareHealthchecks, isScopeMissingForChaining } from '../../common/healthchecks/scopeHealthcheckUtils';
import ExpectationsDriftIndicator from '../../common/injects/expectations/ExpectationsDriftIndicator';
import { countDistinctInjectTargets } from '../../common/injects/utils';
import EntityReportsPanel from '../../reporting/EntityReportsPanel';
import { CONTEXTUAL_ENTITY_WIDGET_IDS, contextualResultsUrl } from '../../workspaces/custom_dashboards/results/contextualWidgets';
import ExerciseDatePopover from './ExerciseDatePopover';
import ExercisePopover, { type ExerciseActionPopover } from './ExercisePopover';
import ExerciseStatus from './ExerciseStatus';
import SimulationConfiguration from './SimulationConfiguration';

const Buttons = ({ exerciseId, exerciseStatus, exerciseName, onLoading, isLoading, isScopeMissing }: {
  exerciseId: Exercise['exercise_id'];
  exerciseStatus: Exercise['exercise_status'];
  exerciseName: Exercise['exercise_name'];
  onLoading: (loading: boolean) => void;
  isLoading: boolean;
  isScopeMissing: boolean;
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const permissions = useSimulationPermissions(exerciseId);
  const [openChangeStatus, setOpenChangeStatus] = useState<Exercise['exercise_status'] | null>(null);

  const submitUpdateStatus = async (status: { exercise_status: Exercise['exercise_status'] | null }) => {
    setOpenChangeStatus(null);
    onLoading(true);
    try {
      await dispatch(updateExerciseStatus(exerciseId, { exercise_status: status.exercise_status ?? undefined }));
    } finally {
      onLoading(false);
    }
  };
  const executionButton = () => {
    switch (exerciseStatus) {
      case 'SCHEDULED': {
        if (permissions.canLaunch) {
          return (
            <Tooltip
              title={isScopeMissing ? t('A chained simulation requires a defined scope.') : ''}
            >
              <span style={{ display: 'inline-flex' }}>
                <Button
                  startIcon={<PlayArrowOutlined />}
                  variant="contained"
                  size="small"
                  color="primary"
                  onClick={() => setOpenChangeStatus('RUNNING')}
                  disabled={isLoading || isScopeMissing}
                >
                  {t('Start now')}
                </Button>
              </span>
            </Tooltip>
          );
        }
        return (<div />);
      }
      case 'RUNNING': {
        if (permissions.canLaunch) {
          return (
            <Button
              startIcon={<PauseOutlined />}
              variant="outlined"
              color="warning"
              size="small"
              onClick={() => setOpenChangeStatus('PAUSED')}
              disabled={isLoading}
            >
              {t('Pause')}
            </Button>
          );
        }
        return (<div />);
      }
      case 'PAUSED': {
        if (permissions.canLaunch) {
          return (
            <Button
              variant="outlined"
              startIcon={<PlayArrowOutlined />}
              color="success"
              size="small"
              onClick={() => setOpenChangeStatus('RUNNING')}
              disabled={isLoading}
            >
              {t('Resume')}
            </Button>
          );
        }
        return <div />;
      }
      default:
        return <div />;
    }
  };

  const dangerousButton = () => {
    switch (exerciseStatus) {
      case 'RUNNING':
      case 'PAUSED': {
        if (permissions.canLaunch) {
          return (
            <Button
              variant="outlined"
              startIcon={<CancelOutlined />}
              color="error"
              size="small"
              onClick={() => setOpenChangeStatus('CANCELED')}
              disabled={isLoading}
            >
              {t('Stop')}
            </Button>
          );
        }
        return <div />;
      }
      case 'FINISHED':
      case 'CANCELED': {
        if (permissions.canLaunch) {
          return (
            <Button
              variant="outlined"
              startIcon={<RestartAltOutlined />}
              color="warning"
              size="small"
              onClick={() => setOpenChangeStatus('SCHEDULED')}
              disabled={isLoading}
            >
              {t('Reset')}
            </Button>
          );
        }
        return <div />;
      }
      default:
        return <div />;
    }
  };

  const dialogContentText = () => {
    switch (openChangeStatus) {
      case 'RUNNING':
        return `${exerciseName} ${t('will be started, do you want to continue?')}`;
      case 'PAUSED':
        return `${t('Injects will be paused, do you want to continue?')}`;
      case 'SCHEDULED':
        return `${exerciseName} ${t('data will be reset, do you want to restart?')}`;
      case 'CANCELED':
        return `${exerciseName} ${t('data will be reset, do you want to restart?')}`;
      default:
        return 'Do you want to change the status of this simulation?';
    }
  };
  return (
    <>
      {executionButton()}
      {dangerousButton()}
      <Dialog
        open={Boolean(openChangeStatus)}
        TransitionComponent={Transition}
        onClose={() => setOpenChangeStatus(null)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {dialogContentText()}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={() => setOpenChangeStatus(null)}>
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={() => submitUpdateStatus({ exercise_status: openChangeStatus })}
          >
            {t('Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

const ExerciseHeader = ({ onLoading, isLoading }: {
  onLoading: (loading: boolean) => void;
  isLoading: boolean;
}) => {
  // Standard hooks
  const theme = useTheme();
  const { t, fldt } = useFormatter();
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useAppDispatch();

  const { exerciseId } = useParams() as { exerciseId: ExerciseType['exercise_id'] };
  const { exercise, challenges, injects, teams, articles } = useHelper((helper: ExercisesHelper & ChallengeHelper & InjectHelper & TeamsHelper & ArticlesHelper) => {
    return {
      exercise: helper.getExercise(exerciseId) as SimulationDetails,
      challenges: helper.getExerciseChallenges(exerciseId) as Challenge[],
      injects: helper.getExerciseInjects(exerciseId) as Inject[],
      teams: helper.getExerciseTeams(exerciseId) as Team[],
      articles: helper.getExerciseArticles(exerciseId) as Article[],
    };
  });
  const permissions = useSimulationPermissions(exerciseId, exercise);

  // Challenges are authored inside injects (no configuration tab): as soon as
  // at least one inject uses a challenge, expose the player-facing preview
  // right in the hero. Injects (lightweight view) feed the usage-aware hero
  // stats: which assets and asset groups the simulation actually targets.
  useDataLoader(() => {
    dispatch(fetchExerciseChallenges(exerciseId));
    dispatch(fetchExerciseInjectsSimple(exerciseId));
    dispatch(fetchExerciseTeams(exerciseId));
    dispatch(fetchExerciseArticles(exerciseId));
  });
  const hasChallenges = challenges.length > 0;

  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const exerciseWorkflowId = exercise.exercise_workflow_id as string | undefined;
  const isSimulationChaining = isChainingFeatureEnabled && !!exerciseWorkflowId;

  const { workflowConfiguration } = useHelper(
    (helper: WorkflowConfigurationHelper) => ({
      workflowConfiguration: exerciseWorkflowId
        ? helper.getWorkflowConfiguration(exerciseWorkflowId)
        : undefined,
    }),
  );

  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);
  const [expectationsDrift, setExpectationsDrift] = useState<ExpectationsDriftOutput | null>(null);
  const [openConfiguration, setOpenConfiguration] = useState(false);
  const [openDateDialog, setOpenDateDialog] = useState(false);

  const isScopeMissing = isScopeMissingForChaining({
    isChaining: isSimulationChaining,
    workflowScopeRules: workflowConfiguration?.workflow_scope_rules ?? [],
    healthchecks,
  });
  const healthchecksForIndicator = getScopeAwareHealthchecks({
    healthchecks,
    isChaining: isSimulationChaining,
    isScopeMissing,
  });

  useEffect(() => {
    searchExerciseHealthchecks(exerciseId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [exerciseId, exercise, workflowConfiguration]);

  // Expectation drift between the injector contract templates and the inject
  // content - recomputed when the simulation or its inject set changes.
  useEffect(() => {
    // The header survives simulation switches (no remount): a stale response from
    // the previous simulation must not overwrite the current one. simpleCall has
    // already notified the user on failure, hence the deliberately empty catch.
    let stale = false;
    fetchExerciseExpectationsDrift(exerciseId)
      .then((result: { data: ExpectationsDriftOutput }) => {
        if (!stale) setExpectationsDrift(result.data);
      })
      .catch(() => {});
    return () => {
      stale = true;
    };
  }, [exerciseId, exercise, injects?.length]);

  const onRealignExpectations = async () => {
    await realignExerciseExpectations(exerciseId);
    const result = await fetchExerciseExpectationsDrift(exerciseId);
    setExpectationsDrift(result.data);
    dispatch(fetchExerciseInjectsSimple(exerciseId));
  };

  // Dismissal is persisted in database (shared between users); the endpoint
  // returns the refreshed drift report.
  const onDismissExpectations = async (dismissed: boolean) => {
    const result = await dismissExerciseExpectationsDrift(exerciseId, dismissed);
    setExpectationsDrift(result.data);
  };

  const actions: ExerciseActionPopover[] = isSimulationChaining
    ? ['Update', 'Export', 'Delete']
    : ['Update', 'Duplicate', 'Export', 'Delete'];

  // Headline stats surfaced right in the hero so they are visible on every
  // tab. The hero adapts to how the simulation is actually built: injects are
  // always shown, while the people dimension (teams, players), the technical
  // dimension (targeted assets, asset groups) and the content dimension
  // (media pressure, challenges) only appear when actually used - a tabletop
  // reads people-first, a technical simulation reads assets-first, and a
  // mixed one shows both.
  // Count the loaded injects list (fetchExerciseInjectsSimple), not
  // `exercise.exercise_injects`: the GET /exercises/{id} SimulationDetails DTO
  // does not carry an injects field, so that path always resolved to 0 after a
  // reload (it only appeared to work right after a create when the redux entity
  // was transiently patched). The injects list is what already feeds the asset
  // counters below, so this keeps every hero counter on the same source.
  const injectsCount = injects?.length ?? 0;
  // Teams and articles are counted from their dedicated list fetches for the
  // same reason as injects: the SimulationDetails DTO carries no
  // exercise_teams / exercise_articles relations.
  const teamsCount = teams?.length ?? 0;
  const playersCount = exercise.exercise_all_users_number ?? exercise.exercise_users_number ?? 0;
  const articlesCount = articles?.length ?? 0;
  const { assets: assetsCount, assetGroups: assetGroupsCount, assetGroupIds } = countDistinctInjectTargets(injects);

  // Countable stats drill down to the full-page results explorer (the same
  // one the dashboards use), scoped to this simulation. Players, media
  // pressure and challenges are not indexed in the engine, so they stay
  // static.
  const statResultsUrl = (entity: string, filterValuesMap?: Record<string, string[] | undefined>) =>
    contextualResultsUrl(
      CONTEXTUAL_ENTITY_WIDGET_IDS[entity],
      'simulation',
      exerciseId,
      location.pathname + location.search,
      filterValuesMap,
    );

  return (
    <>
      <Box sx={{ marginBottom: 2 }}>
        <DetailHero
          icon={PlayCircleOutlineOutlined}
          title={truncate(exercise.exercise_name, 80) ?? ''}
          chips={(
            <>
              <ExerciseStatus exerciseStatus={exercise.exercise_status} exerciseStartDate={exercise.exercise_start_date} variant="list" />
              <ItemSeverity severity={exercise.exercise_severity} label={t(exercise.exercise_severity ?? 'Unknown')} />
              {exercise.exercise_category && (
                <ItemCategory category={exercise.exercise_category} label={t(exercise.exercise_category)} size="small" />
              )}
              <Chip
                size="small"
                variant="outlined"
                label={exercise.exercise_start_date ? fldt(exercise.exercise_start_date) : t('Manual')}
                sx={{
                  borderRadius: 1,
                  height: 22,
                  fontSize: 11,
                  color: theme.palette.text.secondary,
                  borderColor: theme.palette.divider,
                }}
              />
            </>
          )}
          action={(
            <>
              {/* Contextual configuration alert - self-hides when healthy. */}
              {permissions.canManage && (
                <HealthcheckIndicator healthchecks={healthchecksForIndicator} exerciseId={exerciseId} />
              )}
              {/* Expectation drift warning - self-hides when aligned or dismissed. */}
              {permissions.canManage && (
                <ExpectationsDriftIndicator
                  drift={expectationsDrift}
                  variant="simulation"
                  onRealign={onRealignExpectations}
                  onDismiss={onDismissExpectations}
                  placement="warning"
                />
              )}
              {/* Configuration promoted to a first-class button (not buried in the
                  overflow) so teams/players setup is discoverable, with an
                  explicit tooltip describing what it configures. */}
              {permissions.canManage && !isSimulationChaining && (
                <Tooltip title={t('Configure the teams, players and audience involved in this simulation')}>
                  <Button
                    variant="outlined"
                    color="primary"
                    size="small"
                    startIcon={<TuneOutlined />}
                    onClick={() => setOpenConfiguration(true)}
                  >
                    {t('Configuration')}
                  </Button>
                </Tooltip>
              )}
              {/* Dismissed drift downgraded to a discreet icon after Configuration -
                  the drift is acknowledged but still reviewable. */}
              {permissions.canManage && (
                <ExpectationsDriftIndicator
                  drift={expectationsDrift}
                  variant="simulation"
                  onRealign={onRealignExpectations}
                  onDismiss={onDismissExpectations}
                  placement="dismissed"
                />
              )}
              {/* Secondary actions surfaced as compact icon buttons (with explicit
                  tooltips) instead of being buried in the overflow menu.
                  Scheduling can only be modified while the simulation is still
                  SCHEDULED. */}
              {/* Visible as soon as one inject uses a challenge - opens the
                  player-facing challenges page in a new tab. */}
              {hasChallenges && (
                <Tooltip title={t('Preview challenges page')}>
                  <IconButton
                    size="small"
                    color="primary"
                    component={Link}
                    to={`/admin/simulations/${exerciseId}/challenges`}
                    target="_blank"
                  >
                    <EmojiEventsOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
              {/* Entity-scoped reports - self-hides without the reporting
                  access capability. */}
              <EntityReportsPanel
                contextType="SIMULATION"
                contextId={exercise.exercise_id}
                entityName={exercise.exercise_name}
              />
              {permissions.canManage && (
                <>
                  <Tooltip title={t('Modify the scheduling')}>
                    <span style={{ display: 'inline-flex' }}>
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => setOpenDateDialog(true)}
                        disabled={exercise.exercise_status !== 'SCHEDULED'}
                      >
                        <UpdateOutlined fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </>
              )}
              {/* Lifecycle CTAs (start / pause / resume / stop / reset). */}
              <Buttons
                exerciseId={exercise.exercise_id}
                exerciseStatus={exercise.exercise_status}
                exerciseName={exercise.exercise_name}
                onLoading={onLoading}
                isLoading={isLoading}
                isScopeMissing={isScopeMissing}
              />
              {/* CRUD actions in one overflow menu. */}
              <ExercisePopover
                exercise={exercise}
                actions={actions}
                onDelete={() => navigate('/admin/simulations')}
              />
            </>
          )}
          stats={(
            <>
              {/* Always-on core stat. */}
              <HeroStat
                icon={TrackChangesOutlined}
                label={t('Injects')}
                value={injectsCount}
                color={theme.palette.warning.main}
                to={`/admin/simulations/${exerciseId}/injects`}
              />
              {/* People dimension - tabletop / crisis simulations. */}
              {teamsCount > 0 && (
                <HeroStat
                  icon={GroupsOutlined}
                  label={t('Teams')}
                  value={teamsCount}
                  color={theme.palette.secondary.main}
                  to={statResultsUrl('team', { base_id: teams.map((team: Team) => team.team_id) })}
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
              {/* Technical dimension - endpoint-targeting simulations. */}
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

      <Drawer
        open={openConfiguration}
        handleClose={() => setOpenConfiguration(false)}
        title={t('Simulation configuration')}
      >
        <SimulationConfiguration />
      </Drawer>
      <ExerciseDatePopover
        exercise={exercise}
        open={openDateDialog}
        onOpenChange={setOpenDateDialog}
        showTrigger={false}
      />
    </>
  );
};

export default ExerciseHeader;
